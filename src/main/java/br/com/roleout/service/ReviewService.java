package br.com.roleout.service;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.SqlAccess;
import br.com.roleout.dao.OrganizationDAO;
import br.com.roleout.security.Tokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.util.*;

public class ReviewService {
    public record Item(int organizacaoId, Integer localId, OrganizationService.Organization organizacao,
                       OrganizationService.Venue local, String versao) {}
    public record Decision(Integer organizacaoId, Integer localId, String versao, String decisao, String justificativa) {}
    private final SqlAccess sql;
    private final ObjectMapper json = new ObjectMapper();
    public ReviewService() { this(new SqlAccess()); }
    public ReviewService(SqlAccess sql) { this.sql = sql; }
    private ApiException forbidden() { return new ApiException(403, "SEM_PERMISSAO", "A revisão exige um administrador sem vínculo com a organização."); }
    private ApiException conflict() { return new ApiException(409, "REVISAO_DESATUALIZADA", "O cadastro mudou ou não está pendente. Atualize e revise novamente."); }
    private void admin(Connection c, int account, boolean lock) throws SQLException {
        if(!new OrganizationDAO(c).admin(account,lock))throw forbidden();
    }
    private Item item(Connection c, int org, Integer local, boolean lock) throws SQLException {
        var dao=new OrganizationDAO(c);
        var organization=dao.organization(org,lock);
        if(organization==null)throw new ApiException(404,"NAO_ENCONTRADO","Cadastro não encontrado.");
        var venue=local==null?null:dao.venue(org,local,lock);
        if(local!=null&&venue==null)throw new ApiException(404,"NAO_ENCONTRADO","Local não encontrado.");
        try { return new Item(org, local, organization, venue, Tokens.digest(Base64.getEncoder().encodeToString(json.writeValueAsBytes(Arrays.asList(organization, venue))))); }
        catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    public List<Item> pending(int account, int offset) {
        if (offset < 0 || offset > 100000) throw new IllegalArgumentException("Invalid offset.");
        try (var c = sql.connection()) {
            admin(c, account, false);
            var keys=new OrganizationDAO(c).pending(offset);
            var result = new ArrayList<Item>();
            for (var key : keys) result.add(item(c, key[0], key[1] == 0 ? null : key[1], false));
            return result;
        } catch (SQLException e) { throw new RuntimeException("Review queue failed.", e); }
    }
    public Item decide(int account, Decision input) {
        if (input.organizacaoId() == null || input.organizacaoId() < 1 || (input.localId() != null && input.localId() < 1)
                || input.versao() == null || !input.versao().matches("[a-f0-9]{64}")
                || !Set.of("APROVAR", "REJEITAR").contains(input.decisao() == null ? "" : input.decisao())) throw new IllegalArgumentException("Invalid decision.");
        String reason = Inputs.required(input.justificativa(), 2000);
        try (var c = sql.connection()) {
            c.setAutoCommit(false);
            try {
                admin(c, account, true);
                var current = item(c, input.organizacaoId(), input.localId(), true);
                if(new OrganizationDAO(c).hasMember(account,input.organizacaoId()))throw forbidden();
                String status = current.local() == null ? current.organizacao().status() : current.local().status();
                if (!status.equals("PENDENTE") || !current.versao().equals(input.versao())) throw conflict();
                boolean approve = input.decisao().equals("APROVAR");
                if (approve && current.local() != null && !current.organizacao().status().equals("APROVADA"))
                    throw new ApiException(409, "ORGANIZACAO_NAO_APROVADA", "Aprove primeiro a organização.");
                String next = current.local() == null ? (approve ? "APROVADA" : "REJEITADA") : (approve ? "PUBLICADO" : "REJEITADO");
                new OrganizationDAO(c).decide(input.organizacaoId(),input.localId(),account,next,input.decisao(),reason);
                var saved = item(c, input.organizacaoId(), input.localId(), false); c.commit(); return saved;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Review decision failed.", e); }
    }
}
