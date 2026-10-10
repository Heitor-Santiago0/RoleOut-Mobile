package br.com.roleout.service;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.SqlAccess;
import br.com.roleout.security.Passwords;
import br.com.roleout.security.Tokens;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Semaphore;

public class AuthService {
    public record Signup(String nome, String email, String senha) { }
    public record Login(String email, String senha) { }
    public record ProfileInput(String nome) { }
    public record Account(int id, String nome, String email, boolean emailConfirmado, boolean administrador) { }
    public record Session(String token, String expiraEm, Account conta) { }
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
        try (var c = sql.connection(); var s = c.prepareStatement(
                "INSERT INTO contas (nome,email,senha_hash) VALUES (?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            s.setString(1, nome); s.setString(2, email); s.setString(3, hash);
            s.executeUpdate();
            try (var keys = s.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Missing generated account ID.");
                return new Account(keys.getInt(1), nome, email, false, false);
            }
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
        try (var c = sql.connection(); var s = c.prepareStatement("SELECT * FROM contas WHERE email=?")) {
            s.setString(1, email);
            try (var rs = s.executeQuery()) {
                if (rs.next()) {
                    account = account(rs);
                    hash = rs.getString("senha_hash");
                }
            }
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
                // Bound active sessions per account; serialize concurrent logins on this account.
                try (var s = c.prepareStatement("SELECT id FROM contas WHERE id=? FOR UPDATE")) {
                    s.setInt(1, account.id());
                    try (var ignored = s.executeQuery()) { if (!ignored.next()) throw unauthorized(); }
                }
                try (var s = c.prepareStatement("DELETE FROM sessoes WHERE conta_id=? AND expira_em <= ?")) {
                    s.setInt(1, account.id()); s.setTimestamp(2, Timestamp.from(Instant.now())); s.executeUpdate();
                }
                try (var s = c.prepareStatement("SELECT COUNT(*) FROM sessoes WHERE conta_id=?")) {
                    s.setInt(1, account.id());
                    try (var rs = s.executeQuery()) {
                        rs.next();
                        if (rs.getInt(1) >= 10) throw new ApiException(429, "LIMITE_SESSOES", "Encerre uma sessão antes de entrar novamente.");
                    }
                }
                try (var s = c.prepareStatement("INSERT INTO sessoes (token_hash,conta_id,expira_em) VALUES (?,?,?)")) {
                    s.setString(1, Tokens.digest(token)); s.setInt(2, account.id());
                    s.setTimestamp(3, Timestamp.from(expiry)); s.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Session creation failed.", e); }
        return new Session(token, expiry.toString(), account);
    }

    public Account authenticate(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw unauthorized();
        try (var c = sql.connection(); var s = c.prepareStatement(
                "SELECT c.* FROM contas c JOIN sessoes s ON s.conta_id=c.id WHERE s.token_hash=? AND s.expira_em > ?")) {
            s.setString(1, Tokens.digest(token)); s.setTimestamp(2, Timestamp.from(Instant.now()));
            try (var rs = s.executeQuery()) {
                if (!rs.next()) throw unauthorized();
                return account(rs);
            }
        } catch (SQLException e) { throw new RuntimeException("Session validation failed.", e); }
    }

    public Account updateProfile(String token, ProfileInput input) {
        Account current = authenticate(token);
        String name = Inputs.required(input.nome(), 200);
        try (var c = sql.connection(); var s = c.prepareStatement("UPDATE contas SET nome=? WHERE id=?")) {
            s.setString(1, name); s.setInt(2, current.id());
            if (s.executeUpdate() != 1) throw unauthorized();
        } catch (SQLException e) { throw new RuntimeException("Profile update failed.", e); }
        return new Account(current.id(), name, current.email(), current.emailConfirmado(), current.administrador());
    }

    public void logout(String token) {
        authenticate(token);
        try (var c = sql.connection(); var s = c.prepareStatement("DELETE FROM sessoes WHERE token_hash=?")) {
            s.setString(1, Tokens.digest(token)); s.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException("Session revocation failed.", e); }
    }

    private Account account(java.sql.ResultSet rs) throws SQLException {
        return new Account(rs.getInt("id"), rs.getString("nome"), rs.getString("email"),
                rs.getBoolean("email_confirmado"), rs.getBoolean("administrador"));
    }
    private String hash(String password) {
        acquireHashSlot();
        try { return Passwords.hash(password); } finally { HASH_SLOTS.release(); }
    }
    private void acquireHashSlot() {
        if (!HASH_SLOTS.tryAcquire()) throw new ApiException(429, "TENTE_MAIS_TARDE", "Tente novamente em alguns segundos.");
    }
    private ApiException unauthorized() {
        return new ApiException(401, "NAO_AUTENTICADO", "Credenciais ou sessão inválidas.");
    }
}
