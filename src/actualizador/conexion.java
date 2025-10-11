/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package actualizador;

import com.jcraft.jsch.*;
import java.io.*;
import java.util.Vector;

/**
 *
 * @author omega
 */
public class conexion {
    private String servidor;
    private int puerto;
    private String usuario;
    private String password;
    private String rutaBase;
    private Session session;
    private ChannelSftp channel;
    
    // Interfaz para notificar estado de conexión
    public interface ConexionListener {
        void onEstadoConexion(boolean conectado, String mensaje);
    }
    
    private ConexionListener listener;
    
    public conexion(String servidor, int puerto, String usuario, String password, String rutaBase) {
        this.servidor = servidor;
        this.puerto = puerto;
        this.usuario = usuario;
        this.password = password;
        this.rutaBase = rutaBase;
    }
    
    public void setConexionListener(ConexionListener listener) {
        this.listener = listener;
    }
    
    private void notificarEstado(boolean conectado, String mensaje) {
        if (listener != null) {
            listener.onEstadoConexion(conectado, mensaje);
        }
    }
    
    public boolean conectar() {
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(usuario, servidor, puerto);
            session.setPassword(password);
            
            // Configurar propiedades para evitar problemas de conexión
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            config.put("PreferredAuthentications", "password");
            session.setConfig(config);
            
            session.connect(5000); // Timeout de 5 segundos
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(5000);
            
            // Cambiar al directorio base si se especificó
            if (rutaBase != null && !rutaBase.isEmpty()) {
                channel.cd(rutaBase);
            }
            
            notificarEstado(true, "✅ Conectado al servidor SFTP");
            System.out.println("✅ Conexión SFTP establecida: " + servidor);
            return true;
            
        } catch (JSchException | SftpException e) {
            notificarEstado(false, "❌ Error SFTP: " + e.getMessage());
            System.err.println("❌ Error conectando SFTP: " + e.getMessage());
            return false;
        }
    }
    
    public void desconectar() {
        try {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
            notificarEstado(false, "🔌 Desconectado del servidor");
            System.out.println("🔌 Conexión SFTP cerrada");
        } catch (Exception e) {
            System.err.println("Error desconectando SFTP: " + e.getMessage());
        }
    }
    
    public boolean isConectado() {
        return session != null && session.isConnected() && 
               channel != null && channel.isConnected();
    }
    
    /**
     * Lista archivos en un directorio remoto
     */
    public Vector<ChannelSftp.LsEntry> listarArchivos(String directorio) {
        try {
            if (!isConectado()) {
                if (!conectar()) {
                    System.err.println("No se pudo conectar para listar archivos");
                    return new Vector<>();
                }
            }
            return channel.ls(directorio);
        } catch (SftpException e) {
            System.err.println("Error listando archivos en " + directorio + ": " + e.getMessage());
            return new Vector<>();
        }
    }
    
    /**
     * Sube un archivo al servidor
     */
    public boolean subirArchivo(File archivoLocal, String rutaRemota) {
        try {
            if (!isConectado()) {
                conectar();
            }
            channel.put(archivoLocal.getAbsolutePath(), rutaRemota);
            System.out.println("✅ Archivo subido: " + rutaRemota);
            return true;
        } catch (SftpException e) {
            System.err.println("❌ Error subiendo archivo: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Descarga un archivo del servidor
     */
    public boolean descargarArchivo(String rutaRemota, String rutaLocal) {
        try {
            if (!isConectado()) {
                conectar();
            }
            channel.get(rutaRemota, rutaLocal);
            System.out.println("✅ Archivo descargado: " + rutaRemota + " → " + rutaLocal);
            return true;
        } catch (SftpException e) {
            System.err.println("❌ Error descargando archivo: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Elimina un archivo del servidor
     */
    public boolean eliminarArchivo(String rutaRemota) {
        try {
            if (!isConectado()) {
                conectar();
            }
            channel.rm(rutaRemota);
            System.out.println("✅ Archivo eliminado: " + rutaRemota);
            return true;
        } catch (SftpException e) {
            System.err.println("❌ Error eliminando archivo: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Prueba básica de conexión
     */
    public boolean probarConexion() {
        if (conectar()) {
            try {
                // Intentar listar el directorio actual como prueba
                channel.ls(".");
                System.out.println("✅ Prueba de conexión SFTP exitosa");
                return true;
            } catch (SftpException e) {
                System.err.println("❌ Error en prueba de conexión: " + e.getMessage());
                return false;
            } finally {
                desconectar();
            }
        }
        return false;
    }
    
    // Getters
    public String getServidor() { return servidor; }
    public String getUsuario() { return usuario; }
    public String getRutaBase() { return rutaBase; }
}