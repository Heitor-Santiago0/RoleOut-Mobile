package br.com.roleout.service;
import br.com.roleout.model.RoleCharacteristics;
import br.com.roleout.model.OpeningHours;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.SqlAccess;
import br.com.roleout.dao.OrganizationDAO;
import br.com.roleout.dao.RoleDraftDAO;
import br.com.roleout.dao.RolePublicationDAO;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RoleReviewService implements br.com.roleout.model.RoleReviewData {
    private final SqlAccess sql;
    public RoleReviewService() { this(new SqlAccess()); }
    public RoleReviewService(SqlAccess sql) { this.sql = sql; }

    public List<Item> pending(int account, int offset) {
        if (offset < 0 || offset > 100000) throw new IllegalArgumentException("Página inválida.");
        try (var c = sql.connection()) {
            admin(c, account, false);
            var ids=new RoleDraftDAO(c).pending(offset);
            var result = new ArrayList<Item>();
            for (int id : ids) result.add(item(c, id, false));
            return result;
        } catch (SQLException e) { throw new RuntimeException("Role review queue failed.", e); }
    }
    public Item decide(int account, Decision input) {
        if (input == null || input.rascunhoId() == null || input.rascunhoId() < 1 || input.versao() == null || input.versao() < 1
                || !Set.of("APROVAR", "REJEITAR").contains(input.decisao() == null ? "" : input.decisao())) throw new IllegalArgumentException("Decisão inválida.");
        String reason = Inputs.required(input.justificativa(), 2000);
        try (var c = sql.connection()) {
            c.setAutoCommit(false);
            try {
                admin(c, account, true);
                Item current = item(c, input.rascunhoId(), true);
                if(new OrganizationDAO(c).hasMember(account,current.organizacao().id()))throw forbidden();
                var draft = current.role();
                if (!draft.status().equals("PENDENTE") || draft.versao() != input.versao()) throw conflict();
                boolean approve = input.decisao().equals("APROVAR");
                Integer publicId = draft.roleId();
                if (approve) {
                    if (!current.organizacao().status().equals("APROVADA") || !current.local().status().equals("PUBLICADO"))
                        throw new ApiException(409, "LOCAL_NAO_APROVADO", "A organização e o local precisam estar aprovados.");
                    var publications=new RolePublicationDAO(c);
                    if(publicId==null)publicId=publications.insert(current);
                    else if(!publications.update(current,publicId))throw conflict();
                }
                new RoleDraftDAO(c).decide(draft.id(),approve?"PUBLICADO":"REJEITADO",publicId,draft.versao(),account,input.decisao(),reason,priorSnapshot(current.publicacao())+"Proposta analisada:\n"+snapshot(draft));
                Item saved = item(c, draft.id(), false); c.commit(); return saved;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Role review decision failed.", e); }
    }
    private String priorSnapshot(Published previous) {
        if (previous == null) return "Primeira publicação.\n";
        return "Publicação anterior:\nNome: " + previous.nome() + "\nDescrição: " + previous.descricao()
            + "\nCategoria: " + previous.categoria() + "\nData: " + previous.data()
            + "\nInício: " + previous.horario() + "\nTérmino: " + previous.horarioFim() + "\nData de término: " + previous.dataFim() + "\nFuncionamento:\n" + OpeningHours.describe(previous.funcionamento()) + "\nCaracterísticas: " + RoleCharacteristics.describe(previous.caracteristicas())
            + "\nPreço por pessoa: " + previous.valor() + "\nCancelado: " + previous.cancelado()
            + "\nMotivo do cancelamento: " + previous.motivoCancelamento() + "\n\n";
    }
    private String snapshot(RoleDraftService.Draft draft) {
        return "Nome: " + draft.nome() + "\nDescrição: " + draft.descricao()
            + "\nCategoria: " + draft.categoria() + "\nData: " + draft.data()
            + "\nInício: " + draft.horario() + "\nTérmino: " + draft.horarioFim() + "\nData de término: " + draft.dataFim() + "\nFuncionamento:\n" + OpeningHours.describe(draft.funcionamento()) + "\nCaracterísticas: " + RoleCharacteristics.describe(draft.caracteristicas())
            + "\nPreço por pessoa: " + draft.valor()
            + "\nSituação solicitada: " + (draft.cancelado() ? "Cancelado" : "Ativo")
            + "\nMotivo do cancelamento: " + draft.motivoCancelamento();
    }
    private Item item(Connection c, int id, boolean lock) throws SQLException {
        var drafts=new RoleDraftDAO(c);var orgs=new OrganizationDAO(c);
        var keys=drafts.association(id);if(keys==null)throw missing();
        var org=orgs.organization(keys.org(),lock);if(org==null)throw missing();
        var venue=orgs.venue(keys.org(),keys.venue(),lock);if(venue==null)throw missing();
        var draft=drafts.find(keys.venue(),id,lock);if(draft==null)throw missing();
        var publication=draft.roleId()==null?null:new RolePublicationDAO(c).published(draft.roleId());
        if(draft.roleId()!=null&&publication==null)throw conflict();
        return new Item(draft,org,venue,publication);
    }
    private void admin(Connection c,int account,boolean lock)throws SQLException {
        if(!new OrganizationDAO(c).admin(account,lock))throw forbidden();
    }
    private ApiException missing() { return new ApiException(404, "NAO_ENCONTRADO", "Rolê não encontrado."); }
    private ApiException forbidden() { return new ApiException(403, "SEM_PERMISSAO", "A revisão exige um administrador sem vínculo com a organização."); }
    private ApiException conflict() { return new ApiException(409, "REVISAO_DESATUALIZADA", "O rolê mudou ou não está pendente. Atualize a fila."); }
}
