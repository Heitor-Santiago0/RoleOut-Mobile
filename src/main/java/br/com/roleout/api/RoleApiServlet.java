package br.com.roleout.api;

import br.com.roleout.service.RoleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

/** Initial read-only API alongside the original web controllers. */
@WebServlet(urlPatterns = {"/api/v1/roles/*", "/api/v1/sugestoes"})
public final class RoleApiServlet extends HttpServlet {
    private final RoleService service;
    private final ObjectMapper json = new ObjectMapper();

    public RoleApiServlet() { this(new RoleService()); }

    public RoleApiServlet(RoleService service) {
        this.service = java.util.Objects.requireNonNull(service);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        try {
            String path = request.getPathInfo();
            boolean suggestions = "/api/v1/sugestoes".equals(request.getServletPath());
            if (!suggestions && path != null && !path.equals("/")) {
                if (!path.matches("/[1-9][0-9]*")) {
                    error(response, 400, "ID_INVALIDO", "Informe um ID inteiro positivo.");
                    return;
                }
                var role = service.buscarPublicadoPorId(Integer.parseInt(path.substring(1)));
                if (role == null) {
                    error(response, 404, "NAO_ENCONTRADO", "Rolê não encontrado.");
                } else {
                    write(response, 200, RoleDto.from(role));
                }
                return;
            }
            String categoria = request.getParameter("categoria");
            String cidade = request.getParameter("cidade");
            String budget = request.getParameter("valorMaximo");
            if (suggestions && (categoria == null || cidade == null || budget == null)) {
                throw new IllegalArgumentException("Informe categoria, cidade e valorMaximo.");
            }
            Double valorMaximo = budget == null ? null : Double.valueOf(budget);
            int limite = integer(request, "limite", 20);
            int deslocamento = integer(request, "deslocamento", 0);
            var items = service.consultarPagina(categoria, cidade, valorMaximo, limite, deslocamento)
                    .stream().map(RoleDto::from).toList();
            write(response, 200, Map.of("itens", items, "limite", limite, "deslocamento", deslocamento));
        } catch (NumberFormatException exception) {
            error(response, 400, "PARAMETRO_INVALIDO", "Informe parâmetros numéricos válidos.");
        } catch (IllegalArgumentException exception) {
            error(response, 400, "PARAMETRO_INVALIDO", exception.getMessage());
        } catch (RuntimeException exception) {
            // Log details server-side, never expose SQL, JDBC URLs or credentials in JSON.
            if (getServletConfig() != null && getServletContext() != null) {
                getServletContext().log("Falha ao consultar a API de rolês.", exception);
            }
            boolean databaseFailure = causedBySql(exception);
            if (databaseFailure) response.setHeader("Retry-After", "5");
            error(response, databaseFailure ? 503 : 500,
                    databaseFailure ? "BANCO_INDISPONIVEL" : "ERRO_INTERNO",
                    "Não foi possível concluir a consulta. Tente novamente mais tarde.");
        }
    }

    private int integer(HttpServletRequest request, String name, int fallback) {
        String value = request.getParameter(name);
        return value == null ? fallback : Integer.parseInt(value);
    }

    private boolean causedBySql(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException) return true;
        }
        return false;
    }

    private void error(HttpServletResponse response, int status, String code, String message) throws IOException {
        write(response, status, Map.of("codigo", code, "mensagem", message));
    }

    private void write(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        // Servlet container owns the response writer.
        response.getWriter().write(json.writeValueAsString(body));
    }
}
