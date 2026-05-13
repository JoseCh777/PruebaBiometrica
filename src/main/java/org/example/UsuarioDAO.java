package org.example;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;


public class UsuarioDAO {

    // ── Configuración Oracle ──────────────────────────────────────────────
    private static final String URL      = "jdbc:oracle:thin:@localhost:1521:XE";
    private static final String USER     = "huella";
    private static final String PASSWORD = "huella123";

    // ── Conexión ──────────────────────────────────────────────────────────
    private Connection getConexion() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    // ── INSERTAR ──────────────────────────────────────────────────────────
    public boolean insertar(Usuario u) {
        String sql = """
                INSERT INTO USUARIOS_PRUEBA
                    (nombre, apellido, numero_cedula, telefono, huella_template)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection conn = getConexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, u.getNombre());
            ps.setString(2, u.getApellido());
            ps.setString(3, u.getNumeroCedula());
            ps.setString(4, u.getTelefono());
            ps.setBytes (5, u.getTemplateBytes());   // BLOB → byte[]

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("Error al insertar: " + e.getMessage());
            return false;
        }
    }

    // ── ACTUALIZAR ────────────────────────────────────────────────────────
    public boolean actualizar(Usuario u) {
        String sql = """
                UPDATE USUARIOS_PRUEBA
                SET nombre = ?, apellido = ?, telefono = ?, huella_template = ?
                WHERE numero_cedula = ?
                """;
        try (Connection conn = getConexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, u.getNombre());
            ps.setString(2, u.getApellido());
            ps.setString(3, u.getTelefono());
            ps.setBytes (4, u.getTemplateBytes());
            ps.setString(5, u.getNumeroCedula());

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("Error al actualizar: " + e.getMessage());
            return false;
        }
    }

    // ── ELIMINAR ──────────────────────────────────────────────────────────
    public boolean eliminar(String numeroCedula) {
        String sql = "DELETE FROM USUARIOS_PRUEBA WHERE numero_cedula = ?";
        try (Connection conn = getConexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, numeroCedula);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("Error al eliminar: " + e.getMessage());
            return false;
        }
    }

    // ── LISTAR TODOS ──────────────────────────────────────────────────────
    public List<Usuario> listarTodos() {
        List<Usuario> lista = new ArrayList<>();
        String sql = "SELECT id, nombre, apellido, numero_cedula, telefono, huella_template FROM USUARIOS_PRUEBA ORDER BY id";

        try (Connection conn = getConexion();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                lista.add(new Usuario(
                        rs.getInt   ("id"),
                        rs.getString("nombre"),
                        rs.getString("apellido"),
                        rs.getString("numero_cedula"),
                        rs.getString("telefono"),
                        rs.getBytes ("huella_template")
                ));
            }

        } catch (SQLException e) {
            System.err.println("Error al listar: " + e.getMessage());
        }
        return lista;
    }

    // ── BUSCAR POR CEDULA ─────────────────────────────────────────────────
    public Usuario buscarPorCedula(String numeroCedula) {
        String sql = "SELECT id, nombre, apellido, numero_cedula, telefono, huella_template FROM USUARIOS_PRUEBA WHERE numero_cedula = ?";

        try (Connection conn = getConexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, numeroCedula);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Usuario(
                            rs.getInt   ("id"),
                            rs.getString("nombre"),
                            rs.getString("apellido"),
                            rs.getString("numero_cedula"),
                            rs.getString("telefono"),
                            rs.getBytes ("huella_template")
                    );
                }
            }

        } catch (SQLException e) {
            System.err.println("Error al buscar: " + e.getMessage());
        }
        return null;
    }

    // ── PROBAR CONEXIÓN ───────────────────────────────────────────────────
    public boolean probarConexion() {
        try (Connection conn = getConexion()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("Error de conexión: " + e.getMessage());
            return false;
        }
    }
}
