package br.com.roleout.dao;
import java.sql.*;
public final class PreferencesDAO {
    private final Connection c;
    public PreferencesDAO(Connection c){this.c=c;}
    public String content(int account)throws SQLException{
        try(var s=c.prepareStatement("SELECT conteudo FROM preferencias_conta WHERE conta_id=?")){s.setInt(1,account);try(var r=s.executeQuery()){return r.next()?r.getString(1):null;}}
    }
    public boolean lockAccount(int account)throws SQLException{return new ItineraryDAO(c).lockAccount(account);}
    public void save(int account,String content)throws SQLException{
        // The caller locks the account before the existence check, including initial insertion.
        boolean exists=content(account)!=null;
        try(var s=c.prepareStatement(exists?"UPDATE preferencias_conta SET conteudo=? WHERE conta_id=?":"INSERT INTO preferencias_conta (conteudo,conta_id) VALUES (?,?)")){s.setString(1,content);s.setInt(2,account);s.executeUpdate();}
    }
}
