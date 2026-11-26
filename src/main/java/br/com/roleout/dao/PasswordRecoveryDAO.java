package br.com.roleout.dao;
import br.com.roleout.model.PasswordRecoveryData.State;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Caller locks the account before changing recovery state. */
public final class PasswordRecoveryDAO {
    private final Connection c;
    public PasswordRecoveryDAO(Connection c){this.c=c;}
    public Integer account(String email)throws SQLException {
        try(var s=c.prepareStatement("SELECT id FROM contas WHERE email=?")){s.setString(1,email);try(var r=s.executeQuery()){return r.next()?r.getInt(1):null;}}
    }
    public State state(int account)throws SQLException {
        try(var s=c.prepareStatement("SELECT * FROM recuperacoes_senha WHERE conta_id=? FOR UPDATE")){s.setInt(1,account);try(var r=s.executeQuery()){
            return r.next()?new State(r.getTimestamp("solicitado_em").toInstant(),r.getTimestamp("janela_inicio").toInstant(),r.getInt("solicitacoes"),r.getBoolean("pendente"),r.getInt("tentativas"),r.getTimestamp("proxima_tentativa").toInstant()):null;
        }}
    }
    public void enqueue(int account,Instant now,Instant window,int requests,boolean exists)throws SQLException {
        String query=exists?"UPDATE recuperacoes_senha SET solicitado_em=?,janela_inicio=?,solicitacoes=?,pendente=TRUE,tentativas=0,proxima_tentativa=?,token_hash=NULL,expira_em=NULL WHERE conta_id=?":"INSERT INTO recuperacoes_senha(solicitado_em,janela_inicio,solicitacoes,pendente,proxima_tentativa,conta_id) VALUES (?,?,?,TRUE,?,?)";
        try(var s=c.prepareStatement(query)){s.setTimestamp(1,Timestamp.from(now));s.setTimestamp(2,Timestamp.from(window));s.setInt(3,requests);s.setTimestamp(4,Timestamp.from(now));s.setInt(5,account);s.executeUpdate();}
    }
    public List<Integer> due(Instant now)throws SQLException {
        var ids=new ArrayList<Integer>();
        try(var s=c.prepareStatement("SELECT conta_id FROM recuperacoes_senha WHERE pendente=TRUE AND tentativas<3 AND proxima_tentativa<=? AND solicitado_em>? ORDER BY proxima_tentativa LIMIT 10")){
            s.setTimestamp(1,Timestamp.from(now));s.setTimestamp(2,Timestamp.from(now.minusSeconds(3600)));try(var r=s.executeQuery()){while(r.next())ids.add(r.getInt(1));}
        }return ids;
    }
    public String claim(int account,String hash,Instant now)throws SQLException {
        try(var s=c.prepareStatement("UPDATE recuperacoes_senha SET token_hash=?,expira_em=?,tentativas=tentativas+1,proxima_tentativa=? WHERE conta_id=?")){
            s.setString(1,hash);s.setTimestamp(2,Timestamp.from(now.plusSeconds(900)));s.setTimestamp(3,Timestamp.from(now.plusSeconds(120)));s.setInt(4,account);s.executeUpdate();
        }
        try(var s=c.prepareStatement("SELECT email FROM contas WHERE id=?")){s.setInt(1,account);try(var r=s.executeQuery()){return r.next()?r.getString(1):null;}}
    }
    public void delivered(int account,String hash)throws SQLException {
        try(var s=c.prepareStatement("UPDATE recuperacoes_senha SET pendente=FALSE WHERE conta_id=? AND token_hash=?")){s.setInt(1,account);s.setString(2,hash);s.executeUpdate();}
    }
    public Integer valid(String hash,Instant now)throws SQLException {
        try(var s=c.prepareStatement("SELECT conta_id FROM recuperacoes_senha WHERE token_hash=? AND expira_em>?")){s.setString(1,hash);s.setTimestamp(2,Timestamp.from(now));try(var r=s.executeQuery()){return r.next()?r.getInt(1):null;}}
    }
    public void reset(int account,String passwordHash)throws SQLException {
        try(var s=c.prepareStatement("UPDATE contas SET senha_hash=? WHERE id=?")){s.setString(1,passwordHash);s.setInt(2,account);s.executeUpdate();}
        try(var s=c.prepareStatement("DELETE FROM sessoes WHERE conta_id=?")){s.setInt(1,account);s.executeUpdate();}
        try(var s=c.prepareStatement("UPDATE recuperacoes_senha SET token_hash=NULL,expira_em=NULL,pendente=FALSE WHERE conta_id=?")){s.setInt(1,account);s.executeUpdate();}
    }
}
