/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package actualizador;

import java.net.Inet4Address;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 *
 * @author omega
 */

public class ControlVersiones {
    private ConexionBD conexionBD;
    private String idEquipo;
    private String ipEquipo;
    private String nombreEquipo;
    
    public ControlVersiones(ConexionBD conexionBD) {
        this.conexionBD = conexionBD;
        this.nombreEquipo = obtenerNombreEquipo();
        this.ipEquipo = obtenerIpEquipo();
        this.idEquipo = generarIdEquipoUnico();
        
        System.out.println("🖥️ Equipo identificado: " + nombreEquipo);
        System.out.println("🔑 ID Único: " + idEquipo);
        System.out.println("🌐 IP: " + ipEquipo);
    }
    
    // ==================== MÉTODOS PRINCIPALES ====================
    
    /**
     * Obtiene la última versión instalada para este equipo específico
     */
    public int obtenerUltimaVersion(String rfcCliente) {
        String sql = "SELECT ultima_version FROM control_versiones " +
                    "WHERE rfc_cliente = ? AND id_equipo = ? " +
                    "ORDER BY fecha DESC, ultima_actualizacion DESC LIMIT 1";
        
        try {
            PreparedStatement stmt = conexionBD.prepareStatement(sql);
            stmt.setString(1, rfcCliente);
            stmt.setString(2, idEquipo);
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int version = rs.getInt("ultima_version");
                System.out.println("📊 Equipo " + idEquipo + " tiene versión: " + version);
                return version;
            }
        } catch (SQLException e) {
            System.err.println("❌ Error obteniendo última versión: " + e.getMessage());
        }
        
        System.out.println("📊 Equipo " + idEquipo + " no tiene registros (versión 0)");
        return 0; // Si no hay registro, asumir versión 0
    }
    
    /**
     * Registra una nueva actualización para este equipo
     */
    public boolean registrarActualizacion(String rfcCliente, int nuevaVersion, int totalArchivos) {
        String sql = "INSERT INTO control_versiones " +
                    "(rfc_cliente, id_equipo, ip_equipo, nombre_equipo, fecha, " +
                    "ultima_version, total_actualizaciones, ultima_actualizacion) " +
                    "VALUES (?, ?, ?, ?, CURDATE(), ?, ?, NOW()) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "ultima_version = GREATEST(ultima_version, VALUES(ultima_version)), " +
                    "total_actualizaciones = total_actualizaciones + VALUES(total_actualizaciones), " +
                    "ultima_actualizacion = NOW()";
        
        try {
            PreparedStatement stmt = conexionBD.prepareStatement(sql);
            stmt.setString(1, rfcCliente);
            stmt.setString(2, idEquipo);
            stmt.setString(3, ipEquipo);
            stmt.setString(4, nombreEquipo);
            stmt.setInt(5, nuevaVersion);
            stmt.setInt(6, totalArchivos);
            
            boolean resultado = stmt.executeUpdate() > 0;
            if (resultado) {
                System.out.println("✅ Actualización registrada: RFC " + rfcCliente + 
                                 " -> Versión " + nuevaVersion + " (" + totalArchivos + " archivos)");
            }
            return resultado;
        } catch (SQLException e) {
            System.err.println("❌ Error registrando actualización: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Obtiene el historial de actualizaciones de este equipo
     */
    public void obtenerHistorialEquipo(String rfcCliente) {
        String sql = "SELECT fecha, ultima_version, total_actualizaciones, ultima_actualizacion " +
                    "FROM control_versiones " +
                    "WHERE rfc_cliente = ? AND id_equipo = ? " +
                    "ORDER BY fecha DESC LIMIT 10";
        
        try {
            PreparedStatement stmt = conexionBD.prepareStatement(sql);
            stmt.setString(1, rfcCliente);
            stmt.setString(2, idEquipo);
            
            ResultSet rs = stmt.executeQuery();
            System.out.println("📊 Historial de actualizaciones para " + idEquipo + ":");
            boolean tieneRegistros = false;
            while (rs.next()) {
                tieneRegistros = true;
                System.out.println("   📅 " + rs.getDate("fecha") +
                                 " | v" + rs.getInt("ultima_version") +
                                 " | " + rs.getInt("total_actualizaciones") + " archivos" +
                                 " | " + rs.getTimestamp("ultima_actualizacion"));
            }
            if (!tieneRegistros) {
                System.out.println("   ℹ️ No hay historial de actualizaciones");
            }
        } catch (SQLException e) {
            System.err.println("❌ Error obteniendo historial: " + e.getMessage());
        }
    }
    
    /**
 * Verifica si un archivo específico ya fue descargado por este equipo
 */
public boolean yaDescargado(String rfcCliente, String nombreArchivo) {
    String sql = "SELECT 1 FROM archivos_descargados " +
                "WHERE rfc_cliente = ? AND id_equipo = ? AND nombre_archivo = ? " +
                "LIMIT 1";
    
    try {
        PreparedStatement stmt = conexionBD.prepareStatement(sql);
        stmt.setString(1, rfcCliente);
        stmt.setString(2, idEquipo);
        stmt.setString(3, nombreArchivo);
        
        ResultSet rs = stmt.executeQuery();
        return rs.next(); // True si ya existe
    } catch (SQLException e) {
        System.err.println("❌ Error verificando archivo descargado: " + e.getMessage());
        return false;
    }
}

/**
 * Registra un archivo específico como descargado
 */
public boolean registrarArchivoDescargado(String rfcCliente, String nombreArchivo) {
    String sql = "INSERT INTO archivos_descargados " +
                "(rfc_cliente, id_equipo, nombre_archivo) " +
                "VALUES (?, ?, ?)";
    
    try {
        PreparedStatement stmt = conexionBD.prepareStatement(sql);
        stmt.setString(1, rfcCliente);
        stmt.setString(2, idEquipo);
        stmt.setString(3, nombreArchivo);
        
        return stmt.executeUpdate() > 0;
    } catch (SQLException e) {
        System.err.println("❌ Error registrando archivo descargado: " + e.getMessage());
        return false;
    }
}

/**
 * Obtiene el último archivo descargado por este equipo para un RFC
 */
public String obtenerUltimoArchivoDescargado(String rfcCliente) {
    String sql = "SELECT nombre_archivo FROM archivos_descargados " +
                "WHERE rfc_cliente = ? AND id_equipo = ? " +
                "ORDER BY nombre_archivo DESC " +  // Ordenar por nombre (ya está ordenado por fecha)
                "LIMIT 1";
    
    try {
        PreparedStatement stmt = conexionBD.prepareStatement(sql);
        stmt.setString(1, rfcCliente);
        stmt.setString(2, idEquipo);
        
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
            return rs.getString("nombre_archivo");
        }
    } catch (SQLException e) {
        System.err.println("❌ Error obteniendo último archivo: " + e.getMessage());
    }
    return null; // No hay registros
}
    
    // ==================== MÉTODOS DE IDENTIFICACIÓN ====================
    
    /**
     * Genera un ID único para el equipo usando nombre + MAC address
     */
    
    private String generarIdEquipoUnico() {
        String nombre = obtenerNombreEquipo();
    
        // 1. Intentar con MAC address
        String mac = obtenerMacAddressReal();
        if (!mac.equals("unknown")) {
            return nombre + "_" + mac;
        }
    
        // 2. Intentar con IP real (no localhost)
        String ipReal = obtenerIpReal();
        if (!ipReal.equals("127.0.0.1") && !ipReal.equals("127.0.1.1")) {
            return nombre + "_" + ipReal.replace(".", "");
        }
    
        // 3. Usar nombre + usuario del sistema
        String usuario = System.getProperty("user.name");
        if (usuario != null && !usuario.isEmpty()) {
            return nombre + "_" + usuario;
        }
    
        // 4. Último recurso: nombre + timestamp
        return nombre + "_" + System.currentTimeMillis();
    }
    
    /**
     * Obtiene la dirección MAC de la interfaz de red principal
     */
    private String obtenerMacAddressReal() {
    try {
        Enumeration<NetworkInterface> networks = NetworkInterface.getNetworkInterfaces();
        while (networks.hasMoreElements()) {
            NetworkInterface network = networks.nextElement();
            // Solo interfaces activas, no loopback y no virtuales
            if (network.isUp() && !network.isLoopback() && !network.isVirtual()) {
                byte[] mac = network.getHardwareAddress();
                if (mac != null && mac.length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < mac.length; i++) {
                        sb.append(String.format("%02X", mac[i]));
                    }
                    System.out.println("✅ MAC encontrada: " + sb.toString() + " en " + network.getDisplayName());
                    return sb.toString();
                }
            }
        }
    } catch (Exception e) {
        System.err.println("❌ Error obteniendo MAC real: " + e.getMessage());
    }
    return "unknown";
}
    
    /**
     * Obtiene la IP del equipo
     */
    private String obtenerIpEquipo() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            System.err.println("❌ No se pudo obtener IP: " + e.getMessage());
            return "unknown";
        }
    }
    
    
    
    private String obtenerIpReal() {
    try {
        // Obtener IP de interfaz de red activa (no localhost)
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            if (ni.isUp() && !ni.isLoopback()) {
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        }
    } catch (Exception e) {
        System.err.println("❌ Error obteniendo IP real: " + e.getMessage());
    }
    return "127.0.0.1"; // Fallback a localhost
}
    
    /**
     * Obtiene el nombre del equipo
     */
    private String obtenerNombreEquipo() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            System.err.println("❌ No se pudo obtener nombre del equipo: " + e.getMessage());
            return "equipo_desconocido";
        }
    }
    
    // ==================== GETTERS ====================
    
    public String getIdEquipo() {
        return idEquipo;
    }
    
    public String getIpEquipo() {
        return ipEquipo;
    }
    
    public String getNombreEquipo() {
        return nombreEquipo;
    }
    
    public ConexionBD getConexionBD() {
        return conexionBD;
    }
}