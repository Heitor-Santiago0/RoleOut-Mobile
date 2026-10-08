package br.com.roleout.dao;

import br.com.roleout.model.Role;
import br.com.roleout.service.RoleService;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RoleDAOSchemaTest {
    private JdbcDataSource source;
    private RoleDAO dao;

    @BeforeEach
    void schema() throws Exception {
        source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var connection = source.getConnection()) {
            for (String script : new String[]{"V001__roles.sql", "V002__organizacoes_e_locais.sql"}) {
                try (var stream = getClass().getResourceAsStream("/db/migration/" + script);
                     var reader = new InputStreamReader(java.util.Objects.requireNonNull(stream), StandardCharsets.UTF_8)) {
                    RunScript.execute(connection, reader);
                }
            }
        }
        dao = new RoleDAO(source);
    }

    private Role role(String name, double price) {
        return new Role(0, name, "Descrição de teste", "Museu", "Uberaba",
                LocalDate.of(2026, 10, 8), "Cultura", price);
    }

    @Test
    void existingWebCrudAndSuggestionsWorkWithNewSchema() {
        Role original = role("Visita ao museu", 20);
        dao.inserir(original);
        Role saved = dao.listarTodos().get(0);
        assertEquals(original.getData(), saved.getData());
        assertEquals("Museu", saved.getLocal());
        assertEquals(20, saved.getValor());
        assertEquals(saved.getId(), dao.buscarPorId(saved.getId()).getId());
        assertEquals(1, dao.buscarSugestoes("cultura", "uberaba", 20).size());
        assertTrue(dao.buscarSugestoes("Cultura", "Uberaba", 19).isEmpty());
        saved.setNome("Visita atualizada");
        saved.setData(null);
        saved.setValor(0);
        dao.atualizar(saved);
        assertNull(dao.buscarPorId(saved.getId()).getData());
        assertEquals("Visita atualizada", dao.buscarPorId(saved.getId()).getNome());
        dao.excluir(saved.getId());
        assertTrue(dao.listarTodos().isEmpty());
        assertNull(dao.buscarPorId(saved.getId()));
    }

    @Test
    void paginationAndPreparedFiltersDoNotLeakOtherRows() {
        dao.inserir(role("A", 10));
        dao.inserir(role("B", 20));
        dao.inserir(role("C", 30));
        assertEquals("B", dao.consultarPagina(null, null, null, 1, 1).get(0).getNome());
        assertEquals(2, dao.consultarPagina("cultura", "uberaba", 20.0, 10, 0).size());
        assertTrue(dao.consultarPagina("' OR 1=1 --", null, null, 10, 0).isEmpty());
        RoleService service = new RoleService(dao);
        assertThrows(IllegalArgumentException.class, () -> service.consultarPagina(null, null, Double.NaN, 20, 0));
        assertThrows(IllegalArgumentException.class, () -> service.consultarPagina(null, null, 10.0, 101, 0));
        assertThrows(IllegalArgumentException.class, () -> service.consultarPagina(" ", null, null, 20, 0));
    }

    @Test
    void linkedRolesRequireApprovalOfBothOrganizationAndVenue() throws Exception {
        try (var connection = source.getConnection(); var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO organizacoes (nome,tipo,contato_email) VALUES ('Museu teste','MUSEU','museu@example.com')");
            sql.executeUpdate("INSERT INTO locais (organizacao_id,nome,categoria,endereco,cidade,uf) VALUES (1,'Museu teste','Cultura','Rua teste','Uberaba','MG')");
            dao.inserir(role("Exposição", 10));
            int roleId = dao.listarTodos().get(0).getId();
            sql.executeUpdate("UPDATE roles SET local_id=1 WHERE id=" + roleId);
            assertNotNull(dao.buscarPorId(roleId)); // Original web management still works.
            assertNull(dao.buscarPublicadoPorId(roleId));
            assertTrue(dao.consultarPagina(null, null, null, 20, 0).isEmpty());
            sql.executeUpdate("UPDATE locais SET status='PUBLICADO' WHERE id=1");
            assertNull(dao.buscarPublicadoPorId(roleId));
            sql.executeUpdate("UPDATE organizacoes SET status='APROVADA' WHERE id=1");
            assertNotNull(dao.buscarPublicadoPorId(roleId));
            assertEquals(1, dao.consultarPagina(null, null, null, 20, 0).size());
            sql.executeUpdate("UPDATE organizacoes SET status='SUSPENSA' WHERE id=1");
            assertNull(dao.buscarPublicadoPorId(roleId));
            assertThrows(SQLException.class, () -> sql.executeUpdate("DELETE FROM locais WHERE id=1"));
        }
    }

    @Test
    void schemaRejectsInvalidOwnershipStatusAndMoney() throws Exception {
        try (var connection = source.getConnection(); var sql = connection.createStatement()) {
            assertThrows(SQLException.class, () -> sql.executeUpdate("INSERT INTO locais (organizacao_id,nome,categoria,endereco,cidade,uf) VALUES (999,'A','Cultura','Rua','Uberaba','MG')"));
            assertThrows(SQLException.class, () -> sql.executeUpdate("INSERT INTO organizacoes (nome,tipo,contato_email,status) VALUES ('A','BAR','a@example.com','INVALIDO')"));
            assertThrows(RuntimeException.class, () -> dao.inserir(role("Inválido", -1)));
        }
    }
}
