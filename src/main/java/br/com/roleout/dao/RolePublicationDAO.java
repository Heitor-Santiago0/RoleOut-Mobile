package br.com.roleout.dao;
import br.com.roleout.model.RoleReviewData.*;
import br.com.roleout.model.OpeningHours;
import br.com.roleout.model.RoleCharacteristics;
import java.sql.*;
public final class RolePublicationDAO {
    private final Connection c;
    public RolePublicationDAO(Connection c){this.c=c;}
    public int insert(Item current)throws SQLException {
        var draft=current.role();Integer publicId=null;
                        try (var s = c.prepareStatement("INSERT INTO roles(nome,descricao,local,cidade,data,categoria,valor,local_id,horario,horario_fim,data_fim,funcionamento,caracteristicas) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                            s.setString(1, draft.nome()); s.setString(2, draft.descricao()); s.setString(3, current.local().endereco()); s.setString(4, current.local().cidade());
                            if (draft.data() == null) s.setNull(5, Types.DATE); else s.setDate(5, Date.valueOf(draft.data()));
                            s.setString(6, draft.categoria()); s.setBigDecimal(7, draft.valor()); s.setInt(8, draft.localId()); s.setString(9, draft.horario()); s.setString(10, draft.horarioFim()); s.setString(11, draft.dataFim()); s.setString(12, OpeningHours.encode(draft.funcionamento())); s.setString(13, RoleCharacteristics.encode(draft.caracteristicas())); s.executeUpdate();
                            try (var keys = s.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("Missing role ID."); publicId = keys.getInt(1); }
                        }
        return publicId;
    }
    public boolean update(Item current,int publicId)throws SQLException {
        var draft=current.role();
                        try (var s = c.prepareStatement("UPDATE roles SET nome=?,descricao=?,categoria=?,data=?,valor=?,horario=?,horario_fim=?,cancelado=?,motivo_cancelamento=?,data_fim=?,funcionamento=?,caracteristicas=? WHERE id=? AND local_id=?")) {
                            s.setString(1, draft.nome()); s.setString(2, draft.descricao()); s.setString(3, draft.categoria());
                            if (draft.data() == null) s.setNull(4, Types.DATE); else s.setDate(4, Date.valueOf(draft.data()));
                            s.setBigDecimal(5, draft.valor()); s.setString(6, draft.horario()); s.setString(7, draft.horarioFim());
                            s.setBoolean(8, draft.cancelado()); s.setString(9, draft.motivoCancelamento()); s.setString(10, draft.dataFim()); s.setString(11, OpeningHours.encode(draft.funcionamento())); s.setString(12, RoleCharacteristics.encode(draft.caracteristicas())); s.setInt(13, publicId); s.setInt(14, draft.localId());
                            if (s.executeUpdate() != 1) return false;
                        }
        return true;
    }
    public Published published(int id)throws SQLException {
        try (var s = c.prepareStatement("SELECT * FROM roles WHERE id=?")) {
            s.setInt(1, id);
            try (var rs = s.executeQuery()) {
                if (!rs.next()) return null;
                return new Published(rs.getString("nome"), rs.getString("descricao"), rs.getString("categoria"), rs.getString("data"), rs.getBigDecimal("valor"), rs.getString("horario"), rs.getString("horario_fim"), rs.getBoolean("cancelado"), rs.getString("motivo_cancelamento"), rs.getString("data_fim"), OpeningHours.decode(rs.getString("funcionamento")), RoleCharacteristics.decode(rs.getString("caracteristicas")));
            }
        }
    }
}
