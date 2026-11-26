package br.com.roleout.service;
import br.com.roleout.api.ApiException;
import br.com.roleout.dao.*;
import br.com.roleout.model.PasswordRecoveryData;
import br.com.roleout.security.Tokens;
import java.sql.*;
import java.time.*;
import java.util.*;

public final class PasswordRecoveryService implements PasswordRecoveryData {
    private final SqlAccess sql;private final RecoveryMail mail;private final Clock clock;
    public PasswordRecoveryService(){this(new SqlAccess(),RecoveryMailProvider.configured(System.getenv()),Clock.systemUTC());}
    public PasswordRecoveryService(SqlAccess sql,RecoveryMail mail,Clock clock){this.sql=sql;this.mail=mail;this.clock=clock;}
    public boolean enabled(){return mail.enabled();}
    private interface Action<T>{T run(Connection c)throws SQLException;}
    private <T>T transaction(Action<T> action){try(var c=sql.connection()){c.setAutoCommit(false);try{T result=action.run(c);c.commit();return result;}catch(SQLException|RuntimeException e){c.rollback();throw e;}}catch(SQLException e){throw new RuntimeException("Recovery database operation failed",e);}}
    public void request(Request input){
        String email=Inputs.email(input==null?null:input.email());
        if(!enabled())throw new ApiException(503,"RECUPERACAO_INDISPONIVEL","A recuperação por e-mail ainda não está disponível. Tente mais tarde.");
        Instant now=clock.instant();
        transaction(c->{var dao=new PasswordRecoveryDAO(c);Integer account=dao.account(email);if(account==null)return null;
            if(!new AuthDAO(c).lockAccount(account))return null;
            var state=dao.state(account);
            if(state!=null&&state.requested().plusSeconds(60).isAfter(now))return null;
            boolean oldWindow=state==null||!state.window().plusSeconds(3600).isAfter(now);
            if(!oldWindow&&state.requests()>=3)return null;
            dao.enqueue(account,now,oldWindow?now:state.window(),oldWindow?1:state.requests()+1,state!=null);return null;
        });
    }
    public void reset(Reset input){
        if(input==null||input.codigo()==null||!input.codigo().matches("[A-Za-z0-9_-]{43}"))throw invalid();
        String password=Inputs.password(input.senha()),digest=Tokens.digest(input.codigo());
        Integer account=transaction(c->new PasswordRecoveryDAO(c).valid(digest,clock.instant()));
        if(account==null)throw invalid();
        String hash=AuthService.hash(password);
        transaction(c->{if(!new AuthDAO(c).lockAccount(account))throw invalid();var dao=new PasswordRecoveryDAO(c);
            if(!Objects.equals(account,dao.valid(digest,clock.instant())))throw invalid();
            dao.reset(account,hash);return null;
        });
    }
    private ApiException invalid(){return new ApiException(400,"CODIGO_INVALIDO","Código inválido ou expirado. Solicite um novo código.");}
    /** Durable queue, with a two-minute lease and at most three delivery attempts. */
    public int dispatch(){
        if(!enabled())return 0;
        var ids=transaction(c->new PasswordRecoveryDAO(c).due(clock.instant()));int delivered=0;
        for(int id:ids){
            String code=Tokens.create(),digest=Tokens.digest(code);Instant now=clock.instant();
            String email=transaction(c->{if(!new AuthDAO(c).lockAccount(id))return null;var dao=new PasswordRecoveryDAO(c);var state=dao.state(id);
                if(state==null||!state.pending()||state.attempts()>=3||state.next().isAfter(now)||!state.requested().plusSeconds(3600).isAfter(now))return null;
                return dao.claim(id,digest,now);
            });
            if(email==null)continue;
            try{mail.send(email,code);transaction(c->{new PasswordRecoveryDAO(c).delivered(id,digest);return null;});delivered++;}
            catch(Exception e){java.util.logging.Logger.getLogger(PasswordRecoveryService.class.getName()).warning("Recovery delivery failed; durable retry retained.");}
        }return delivered;
    }
}
