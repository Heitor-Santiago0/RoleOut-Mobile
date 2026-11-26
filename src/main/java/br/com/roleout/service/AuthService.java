package br.com.roleout.service;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.SqlAccess;
import br.com.roleout.security.Passwords;
import br.com.roleout.security.Tokens;
import br.com.roleout.dao.AuthDAO;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Semaphore;

public class AuthService implements br.com.roleout.model.AuthData {
    private final SqlAccess sql;
    private static final Semaphore HASH_SLOTS = new Semaphore(2);
    private static final class Dummy {
        static final String HASH = Passwords.hash(Tokens.create());
    }
    public AuthService() { this(new SqlAccess()); }
    public AuthService(SqlAccess sql) { this.sql = sql; }

    public Account signup(Signup input) {
        String nome = Inputs.required(input.nome(), 200);
        String email = Inputs.email(input.email());
        String password = Inputs.password(input.senha());
        String hash = hash(password);
        try (var c = sql.connection()) {
            return new Account(new AuthDAO(c).insert(nome,email,hash),nome,email,false,false);
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState()) || e.getErrorCode() == 1062) {
                throw new ApiException(409, "CADASTRO_INDISPONIVEL", "Não foi possível cadastrar esse e-mail.");
            }
            throw new RuntimeException("Account registration failed.", e);
        }
    }

    public Session login(Login input) {
        String email = Inputs.email(input.email());
        String password = Inputs.password(input.senha());
        Account account = null;
        String hash = null;
        // Release JDBC connection before doing expensive password work.
        try (var c = sql.connection()) {
            var credentials=new AuthDAO(c).credentials(email);
            if(credentials!=null){account=credentials.account();hash=credentials.hash();}
        } catch (SQLException e) { throw new RuntimeException("Account lookup failed.", e); }
        boolean valid;
        acquireHashSlot();
        try { valid = Passwords.verify(password, hash == null ? Dummy.HASH : hash); }
        finally { HASH_SLOTS.release(); }
        if (!valid || account == null) throw unauthorized();
        String token = Tokens.create();
        Instant expiry = Instant.now().plus(24, ChronoUnit.HOURS);
        try (var c = sql.connection()) {
            c.setAutoCommit(false);
            try {
                var dao=new AuthDAO(c);
                if(!dao.lockAccount(account.id()))throw unauthorized();
                // A password reset may have committed while verification ran outside the transaction.
                var current=dao.credentials(email);
                if(current==null||!java.util.Objects.equals(hash,current.hash()))throw unauthorized();
                dao.removeExpired(account.id(),Instant.now());
                if(dao.sessions(account.id())>=10)throw new ApiException(429,"LIMITE_SESSOES","Encerre uma sessão antes de entrar novamente.");
                dao.insertSession(Tokens.digest(token),account.id(),expiry);
                c.commit();
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Session creation failed.", e); }
        return new Session(token, expiry.toString(), account);
    }

    public Account authenticate(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw unauthorized();
        try (var c = sql.connection()) {
            var account=new AuthDAO(c).authenticated(Tokens.digest(token),Instant.now());
            if(account==null)throw unauthorized();return account;
        } catch (SQLException e) { throw new RuntimeException("Session validation failed.", e); }
    }

    public Account updateProfile(String token, ProfileInput input) {
        Account current = authenticate(token);
        String name = Inputs.required(input.nome(), 200);
        try (var c = sql.connection()) {
            if(!new AuthDAO(c).rename(current.id(),name))throw unauthorized();
        } catch (SQLException e) { throw new RuntimeException("Profile update failed.", e); }
        return new Account(current.id(), name, current.email(), current.emailConfirmado(), current.administrador());
    }

    public void logout(String token) {
        authenticate(token);
        try (var c = sql.connection()) {
            new AuthDAO(c).logout(Tokens.digest(token));
        } catch (SQLException e) { throw new RuntimeException("Session revocation failed.", e); }
    }

    static String hash(String password) {
        acquireHashSlot();
        try { return Passwords.hash(password); } finally { HASH_SLOTS.release(); }
    }
    private static void acquireHashSlot() {
        if (!HASH_SLOTS.tryAcquire()) throw new ApiException(429, "TENTE_MAIS_TARDE", "Tente novamente em alguns segundos.");
    }
    private ApiException unauthorized() {
        return new ApiException(401, "NAO_AUTENTICADO", "Credenciais ou sessão inválidas.");
    }
}
