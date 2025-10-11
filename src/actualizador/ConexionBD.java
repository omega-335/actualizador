package actualizador;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

import java.sql.*;
import java.util.Properties;
/**
 *
 * @author omega
 */

public class ConexionBD {
    private Connection conexion;
    private String url;
    private String usuario;
    private String password;
    
    // Configuración por defecto (ajusta estos valores)
    public ConexionBD() {
        this.url = "jdbc:mysql://localhost:3306/tu_base_datos";
        this.usuario = "root";
        this.password = "eltamaldesofia25";
    }
    
    // Constructor con parámetros personalizados
    public ConexionBD(String host, String puerto, String baseDatos, String usuario, String password) {
        this.url = "jdbc:mysql://" + host + ":" + puerto + "/" + baseDatos;
        this.usuario = usuario;
        this.password = password;
    }
    
    /**
     * Establece conexión con la base de datos MySQL
     */
    public boolean conectar() {
        try {
            // Cargar el driver de MySQL
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            // Propiedades adicionales para mejor rendimiento
            Properties propiedades = new Properties();
            propiedades.setProperty("user", usuario);
            propiedades.setProperty("password", password);
            propiedades.setProperty("useSSL", "false");
            propiedades.setProperty("autoReconnect", "true");
            propiedades.setProperty("characterEncoding", "UTF-8");
            propiedades.setProperty("useUnicode", "true");
            
            // Establecer conexión
            conexion = DriverManager.getConnection(url, propiedades);
            System.out.println("✅ Conexión MySQL establecida: " + url);
            return true;
            
        } catch (ClassNotFoundException e) {
            System.err.println("❌ Error: Driver MySQL no encontrado");
            System.err.println("   Asegúrate de tener mysql-connector-j en el classpath");
            return false;
        } catch (SQLException e) {
            System.err.println("❌ Error conectando a MySQL: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Cierra la conexión con la base de datos
     */
    public void desconectar() {
        try {
            if (conexion != null && !conexion.isClosed()) {
                conexion.close();
                System.out.println("🔌 Conexión MySQL cerrada");
            }
        } catch (SQLException e) {
            System.err.println("Error cerrando conexión: " + e.getMessage());
        }
    }
    
    /**
     * Verifica si la conexión está activa
     */
    public boolean estaConectada() {
        try {
            return conexion != null && !conexion.isClosed() && conexion.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }
    
    /**
     * Ejecuta una consulta SELECT y retorna el ResultSet
     */
    public ResultSet ejecutarConsulta(String sql) throws SQLException {
        if (!estaConectada()) {
            conectar();
        }
        Statement stmt = conexion.createStatement();
        return stmt.executeQuery(sql);
    }
    
    /**
     * Ejecuta una consulta INSERT, UPDATE o DELETE
     */
    public int ejecutarActualizacion(String sql) throws SQLException {
        if (!estaConectada()) {
            conectar();
        }
        Statement stmt = conexion.createStatement();
        return stmt.executeUpdate(sql);
    }
    
    /**
     * Prepara un PreparedStatement para evitar SQL injection
     */
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        if (!estaConectada()) {
            conectar();
        }
        return conexion.prepareStatement(sql);
    }
    
    // Getters
    public Connection getConexion() { return conexion; }
    public String getUrl() { return url; }
    
    /**
     * Prueba básica de conexión
     */
    public boolean probarConexion() {
        if (conectar()) {
            try {
                // Consulta simple para verificar
                ResultSet rs = ejecutarConsulta("SELECT 1");
                if (rs.next()) {
                    System.out.println("✅ Prueba de conexión MySQL exitosa");
                    return true;
                }
            } catch (SQLException e) {
                System.err.println("❌ Error en prueba de conexión: " + e.getMessage());
            } finally {
                desconectar();
            }
        }
        return false;
    }
}
