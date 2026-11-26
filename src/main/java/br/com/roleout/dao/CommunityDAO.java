package br.com.roleout.dao;

import br.com.roleout.model.CommunityData.*;
import br.com.roleout.model.SocialProfileData;
import java.sql.*;
import java.util.*;

/** Community persistence; callers own authorization and transactions. */
public final class CommunityDAO {
    private final Connection c;
    public CommunityDAO(Connection c){this.c=c;}
    public boolean blocked(String type,String target)throws SQLException {
        try(var s=c.prepareStatement("SELECT 1 FROM comunidade_bloqueios WHERE tipo=? AND alvo=?")){
            s.setString(1,type);s.setString(2,target);try(var r=s.executeQuery()){return r.next();}
        }
    }
    public Feed feed(int account,boolean following,long after)throws SQLException {
        String query="SELECT p.roteiro_id,p.id_publico,p.titulo,p.cidade,p.paradas,a.id_publico,a.nome,a.apresentacao,LEFT(p.relato,180),(SELECT COUNT(*) FROM publicacoes_fotos f WHERE f.roteiro_id=p.roteiro_id) FROM publicacoes_roteiros p JOIN perfis_sociais a ON a.conta_id=p.conta_id WHERE p.publico=TRUE AND a.publico=TRUE AND p.roteiro_id<? AND NOT EXISTS(SELECT 1 FROM comunidade_bloqueios b WHERE b.tipo='PUBLICACAO' AND b.alvo=p.id_publico)"+
            (following?" AND EXISTS(SELECT 1 FROM comunidade_seguidos f WHERE f.conta_id=? AND f.perfil_id=a.id_publico)":"")+" ORDER BY p.roteiro_id DESC LIMIT 21";
        var items=new ArrayList<FeedItem>();long cursor=0;
        try(var s=c.prepareStatement(query)){
            s.setLong(1,after);if(following)s.setInt(2,account);
            try(var r=s.executeQuery()){while(r.next()){
                if(items.size()==20)return new Feed(List.copyOf(items),cursor);
                int stops;try{stops=new com.fasterxml.jackson.databind.ObjectMapper().readTree(r.getString(5)).size();}catch(java.io.IOException e){throw new SQLException(e);}
                items.add(new FeedItem(r.getString(2),r.getString(3),r.getString(4),stops,new SocialProfileData.Public(r.getString(6),r.getString(7),r.getString(8)),r.getString(9),r.getInt(10)));cursor=r.getLong(1);
            }}
        }return new Feed(List.copyOf(items),null);
    }
    /** Select only the first photo, and authorize in the same query as the image read. */
    public br.com.roleout.model.PublicationData.Photo cover(String publication)throws SQLException {
        String query="SELECT f.imagem,f.legenda FROM publicacoes_fotos f JOIN publicacoes_roteiros p ON p.roteiro_id=f.roteiro_id JOIN perfis_sociais a ON a.conta_id=p.conta_id WHERE p.id_publico=? AND p.publico=TRUE AND a.publico=TRUE AND NOT EXISTS(SELECT 1 FROM comunidade_bloqueios b WHERE b.tipo='PUBLICACAO' AND b.alvo=p.id_publico) ORDER BY f.ordem LIMIT 1";
        try(var s=c.prepareStatement(query)){
            s.setString(1,publication);
            try(var r=s.executeQuery()){return r.next()?new br.com.roleout.model.PublicationData.Photo(Base64.getEncoder().encodeToString(r.getBytes(1)),r.getString(2)):null;}
        }
    }
    public boolean follows(int account,String profile)throws SQLException {
        try(var s=c.prepareStatement("SELECT 1 FROM comunidade_seguidos WHERE conta_id=? AND perfil_id=?")){s.setInt(1,account);s.setString(2,profile);try(var r=s.executeQuery()){return r.next();}}
    }
    public void follow(int account,String profile,boolean value)throws SQLException {
        if(value&&follows(account,profile))return;
        try(var s=c.prepareStatement(value?"INSERT INTO comunidade_seguidos(conta_id,perfil_id) VALUES (?,?)":"DELETE FROM comunidade_seguidos WHERE conta_id=? AND perfil_id=?")){s.setInt(1,account);s.setString(2,profile);s.executeUpdate();}
    }
    public boolean visited(int account,int role)throws SQLException {
        try(var s=c.prepareStatement("SELECT 1 FROM roteiro_paradas p JOIN roteiros r ON r.id=p.roteiro_id WHERE r.conta_id=? AND p.role_id=? AND p.concluida=TRUE AND r.data_planejada<=CURRENT_DATE")){
            s.setInt(1,account);s.setInt(2,role);try(var r=s.executeQuery()){return r.next();}
        }
    }
    public Entries entries(int account,String type,String target,long after)throws SQLException {
        String visible=" FROM comunidade_textos t JOIN perfis_sociais a ON a.conta_id=t.conta_id WHERE t.tipo=? AND t.alvo=? AND t.oculto=FALSE AND a.publico=TRUE";
        int total;Double average;
        try(var s=c.prepareStatement("SELECT COUNT(*),AVG(t.nota)"+visible)){s.setString(1,type);s.setString(2,target);try(var r=s.executeQuery()){r.next();total=r.getInt(1);average=r.getObject(2)==null?null:r.getDouble(2);}}
        var items=new ArrayList<Entry>();
        try(var s=c.prepareStatement("SELECT t.id,a.id_publico,a.nome,a.apresentacao,t.texto,t.nota,t.conta_id"+visible+" AND t.id>? ORDER BY t.id LIMIT 21")){
            s.setString(1,type);s.setString(2,target);s.setLong(3,after);try(var r=s.executeQuery()){while(r.next()){
                items.add(new Entry(r.getLong(1),new SocialProfileData.Public(r.getString(2),r.getString(3),r.getString(4)),r.getString(5),r.getObject(6)==null?null:r.getInt(6),r.getInt(7)==account));
            }}
        }
        boolean more=items.size()>20;if(more)items.remove(20);
        return new Entries(List.copyOf(items),more?items.get(19).id():null,total,average);
    }
    public void text(int account,String type,String target,String text,Integer score)throws SQLException {
        Long id=null;
        try(var s=c.prepareStatement("SELECT id,oculto FROM comunidade_textos WHERE conta_id=? AND tipo=? AND alvo=?")){s.setInt(1,account);s.setString(2,type);s.setString(3,target);try(var r=s.executeQuery()){if(r.next()){id=r.getLong(1);}}}
        // Moderated entries remain hidden on edits; a new edit cannot bypass a decision.
        try(var s=c.prepareStatement(id==null?"INSERT INTO comunidade_textos(texto,nota,conta_id,tipo,alvo) VALUES (?,?,?,?,?)":"UPDATE comunidade_textos SET texto=?,nota=? WHERE conta_id=? AND tipo=? AND alvo=?")){
            s.setString(1,text);if(score==null)s.setNull(2,Types.INTEGER);else s.setInt(2,score);s.setInt(3,account);s.setString(4,type);s.setString(5,target);s.executeUpdate();
        }
    }
    public void deleteText(int account,String type,String target)throws SQLException {
        try(var s=c.prepareStatement("DELETE FROM comunidade_textos WHERE conta_id=? AND tipo=? AND alvo=? AND oculto=FALSE")){s.setInt(1,account);s.setString(2,type);s.setString(3,target);s.executeUpdate();}
    }
    public String textTarget(long id)throws SQLException {
        try(var s=c.prepareStatement("SELECT tipo,alvo FROM comunidade_textos WHERE id=? AND oculto=FALSE")){s.setLong(1,id);try(var r=s.executeQuery()){return r.next()?r.getString(1)+":"+r.getString(2):null;}}
    }
    public boolean reported(int account,String type,String target)throws SQLException {
        try(var s=c.prepareStatement("SELECT 1 FROM comunidade_denuncias WHERE conta_id=? AND tipo=? AND alvo=?")){s.setInt(1,account);s.setString(2,type);s.setString(3,target);try(var r=s.executeQuery()){return r.next();}}
    }
    public void report(int account,ReportInput input)throws SQLException {
        try(var s=c.prepareStatement("INSERT INTO comunidade_denuncias(conta_id,tipo,alvo,motivo) VALUES (?,?,?,?)")){s.setInt(1,account);s.setString(2,input.tipo());s.setString(3,input.alvo());s.setString(4,input.motivo());s.executeUpdate();}
    }
    public List<Report> reports(long after)throws SQLException {
        var items=new ArrayList<Report>();
        try(var s=c.prepareStatement("SELECT d.*,p.titulo,p.relato,t.texto FROM comunidade_denuncias d LEFT JOIN publicacoes_roteiros p ON d.tipo='PUBLICACAO' AND p.id_publico=d.alvo LEFT JOIN comunidade_textos t ON d.tipo='TEXTO' AND CAST(t.id AS CHAR)=d.alvo WHERE d.estado='PENDENTE' AND d.id>? ORDER BY d.id LIMIT 20")){
            s.setLong(1,after);try(var r=s.executeQuery()){while(r.next())items.add(new Report(r.getLong("id"),r.getString("tipo"),r.getString("alvo"),r.getString("motivo"),r.getString("texto")!=null?r.getString("texto"):Objects.toString(r.getString("titulo"),"Conteúdo removido")+"\n"+Objects.toString(r.getString("relato"),""),r.getString("estado")));}
        }return items;
    }
    public Report lockReport(long id)throws SQLException {
        try(var s=c.prepareStatement("SELECT * FROM comunidade_denuncias WHERE id=? FOR UPDATE")){s.setLong(1,id);try(var r=s.executeQuery()){return r.next()?new Report(id,r.getString("tipo"),r.getString("alvo"),r.getString("motivo"),"",r.getString("estado")):null;}}
    }
    public void decide(long id,int admin,Report item,Decision decision)throws SQLException {
        if(decision.ocultar()){
            if(item.tipo().equals("PUBLICACAO")&&!blocked("PUBLICACAO",item.alvo()))try(var s=c.prepareStatement("INSERT INTO comunidade_bloqueios(tipo,alvo) VALUES ('PUBLICACAO',?)")){s.setString(1,item.alvo());s.executeUpdate();}
            if(item.tipo().equals("TEXTO"))try(var s=c.prepareStatement("UPDATE comunidade_textos SET oculto=TRUE WHERE id=?")){s.setLong(1,Long.parseLong(item.alvo()));s.executeUpdate();}
        }
        try(var s=c.prepareStatement("UPDATE comunidade_denuncias SET estado=?,decisao=?,revisor_id=? WHERE id=?")){s.setString(1,decision.ocultar()?"OCULTADO":"MANTIDO");s.setString(2,decision.justificativa());s.setInt(3,admin);s.setLong(4,id);s.executeUpdate();}
    }
    public void credit(int route,Credit value)throws SQLException {
        try(var s=c.prepareStatement("INSERT INTO roteiro_creditos(roteiro_id,publicacao_id,autor_id,autor_nome,titulo) VALUES (?,?,?,?,?)")){s.setInt(1,route);s.setString(2,value.publicacaoId());s.setString(3,value.autorId());s.setString(4,value.autorNome());s.setString(5,value.titulo());s.executeUpdate();}
    }
    public Credit publicCredit(String id)throws SQLException {
        try(var s=c.prepareStatement("SELECT roteiro_id FROM publicacoes_roteiros WHERE id_publico=?")){s.setString(1,id);try(var r=s.executeQuery()){return r.next()?credit(r.getInt(1)):null;}}
    }
    public Credit credit(int route)throws SQLException {
        try(var s=c.prepareStatement("SELECT * FROM roteiro_creditos WHERE roteiro_id=?")){s.setInt(1,route);try(var r=s.executeQuery()){return r.next()?new Credit(r.getString("publicacao_id"),r.getString("autor_id"),r.getString("autor_nome"),r.getString("titulo")):null;}}
    }
}
