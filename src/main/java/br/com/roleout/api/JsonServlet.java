package br.com.roleout.api;

import br.com.roleout.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;

public abstract class JsonServlet extends HttpServlet {
    private final ObjectMapper json = new ObjectMapper()
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    protected final AuthService auth;
    protected JsonServlet(AuthService auth) { this.auth = auth; }
    protected record Reply(int status, Object body) { }
    protected abstract Reply handle(HttpServletRequest request) throws IOException;

    @Override
    protected final void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        Reply reply;
        try { reply = handle(request); }
        catch (ApiException e) {
            if (e.status() == 401) response.setHeader("WWW-Authenticate", "Bearer");
            if (e.status() == 429) response.setHeader("Retry-After", "60");
            reply = error(e.status(), e.code(), e.getMessage());
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            reply = error(400, "PARAMETRO_INVALIDO", "Confira o JSON e os campos informados.");
        } catch (RuntimeException e) {
            boolean database = false;
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException) database = true;
            }
            if (getServletConfig() != null) getServletContext().log("Falha na API de cadastros.", e);
            reply = error(database ? 503 : 500, database ? "BANCO_INDISPONIVEL" : "ERRO_INTERNO",
                    "Não foi possível concluir a operação.");
        }
        response.setStatus(reply.status());
        if (reply.body() != null) response.getWriter().write(json.writeValueAsString(reply.body()));
    }
    private Reply error(int status, String code, String message) {
        return new Reply(status, Map.of("codigo", code, "mensagem", message));
    }
    protected String token(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) throw new ApiException(401, "NAO_AUTENTICADO", "Entre na sua conta.");
        return header.substring(7);
    }
    protected int accountId(HttpServletRequest request) { return auth.authenticate(token(request)).id(); }
    protected <T> T body(HttpServletRequest request, Class<T> type) throws IOException {
        String content = request.getContentType();
        if (content == null || !content.split(";", 2)[0].trim().toLowerCase(Locale.ROOT).equals("application/json")) {
            throw new ApiException(415, "TIPO_INVALIDO", "Envie application/json.");
        }
        if (request.getContentLengthLong() > 16384) throw new ApiException(413, "CORPO_MUITO_GRANDE", "Corpo excede o limite.");
        var reader = request.getReader();
        StringBuilder text = new StringBuilder();
        char[] buffer = new char[1024];
        int size;
        while ((size = reader.read(buffer)) != -1) {
            text.append(buffer, 0, size);
            if (text.length() > 16384) throw new ApiException(413, "CORPO_MUITO_GRANDE", "Corpo excede o limite.");
        }
        T value = json.readValue(text.toString(), type);
        if (value == null) throw new IllegalArgumentException("Null JSON.");
        return value;
    }
    protected ApiException missing() { return new ApiException(404, "NAO_ENCONTRADO", "Endpoint não encontrado."); }
    protected ApiException method() { return new ApiException(405, "METODO_INVALIDO", "Método não permitido."); }
}
