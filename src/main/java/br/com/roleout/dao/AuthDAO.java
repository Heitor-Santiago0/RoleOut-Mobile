package br.com.roleout.dao;
import br.com.roleout.model.AuthData.Account;
import java.sql.*;
import java.time.Instant;
public final class AuthDAO {
    public record Credentials(Account account,String hash) {}
    private final Connection c;
    public AuthDAO(Connection c){this.c=c;}
    public int insert(String name,String email,String hash)throws SQLException {
        try(var s=c.prepareStatement("INSERT INTO contas (nome,email,senha_hash) VALUES (?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
            s.setString(1,name);s.setString(2,email);s.setString(3,hash);s.executeUpdate();try(var r=s.getGeneratedKeys()){if(!r.next())throw new SQLException("Missing account ID");return r.getInt(1);}
        }
    }
    public Credentials credentials(String email)throws SQLException {
        try(var s=c.prepareStatement("SELECT * FROM contas WHERE email=?")){s.setString(1,email);try(var r=s.executeQuery()){return r.next()?new Credentials(account(r),r.getString("senha_hash")):null;}}
    }
    public boolean lockAccount(int id)throws SQLException{return new ItineraryDAO(c).lockAccount(id);}
    public void removeExpired(int id,Instant now)throws SQLException {
        try(var s=c.prepareStatement("DELETE FROM sessoes WHERE conta_id=? AND expira_em <= ?")){s.setInt(1,id);s.setTimestamp(2,Timestamp.from(now));s.executeUpdate();}
    }
    public int sessions(int id)throws SQLException {
        try(var s=c.prepareStatement("SELECT COUNT(*) FROM sessoes WHERE conta_id=?")){s.setInt(1,id);try(var r=s.executeQuery()){r.next();return r.getInt(1);}}
    }
    public void insertSession(String digest,int id,Instant expiry)throws SQLException {
        try(var s=c.prepareStatement("INSERT INTO sessoes (token_hash,conta_id,expira_em) VALUES (?,?,?)")){s.setString(1,digest);s.setInt(2,id);s.setTimestamp(3,Timestamp.from(expiry));s.executeUpdate();}
    }
    public Account authenticated(String digest,Instant now)throws SQLException {
        try(var s=c.prepareStatement("SELECT c.* FROM contas c JOIN sessoes s ON s.conta_id=c.id WHERE s.token_hash=? AND s.expira_em > ?")) {
            s.setString(1,digest);s.setTimestamp(2,Timestamp.from(now));try(var r=s.executeQuery()){return r.next()?account(r):null;}
        }
    }
    public boolean rename(int id,String name)throws SQLException {
        try(var s=c.prepareStatement("UPDATE contas SET nome=? WHERE id=?")){s.setString(1,name);s.setInt(2,id);return s.executeUpdate()==1;}
    }
    public void logout(String digest)throws SQLException {
        try(var s=c.prepareStatement("DELETE FROM sessoes WHERE token_hash=?")){s.setString(1,digest);s.executeUpdate();}
    }
    private Account account(ResultSet r)throws SQLException{return new Account(r.getInt("id"),r.getString("nome"),r.getString("email"),r.getBoolean("email_confirmado"),r.getBoolean("administrador"));}
}
