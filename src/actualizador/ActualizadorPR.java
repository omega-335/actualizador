/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package actualizador;

import com.jcraft.jsch.ChannelSftp;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileInputStream;
import java.lang.management.ManagementFactory;
import java.util.Optional;
import java.util.Properties;
import java.util.Vector;
import javax.swing.*;
import net.miginfocom.swing.*;

/**
 *
 * @author omega
 */
public class ActualizadorPR extends javax.swing.JFrame {
    
    private conexion conexionSFTP;
    private ConexionBD conexionBD;
    private Vector<String> listaRFCs = new Vector<>();
    private static String srv, esquemaDb, userDb, passDb;
    private ControlVersiones controlVersiones;
    private boolean contadorPausado = false;
    private int segundosRestantes = 5;
    private Thread hiloContador;//si ya estamos aqui con control z estamos en la version todavia funcional 

    /**
     * Creates new form ActualizadorPR
     */
    public ActualizadorPR() {
        //setUndecorated(true);//eliminar si quiero que salga la barra 
        initComponents(); 
        cerrarTodoMenosActualizador();
        hacerVentanaNoCerrable();
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        configurarInterfaz();
        configurarTeclaF7();// aqui hacemos el forazado de todas las actualizaciones por si el caso de que algo se nos paso 
        cargarConfiguracion();
        inicializarConexionBD();
        System.out.println("🔍 DEBUG - Estado antes de ControlVersiones:");
        System.out.println("   - conexionBD es null: " + (conexionBD == null));
    
        // ✅ INICIALIZAR ControlVersiones
        if (conexionBD != null) {
            System.out.println("🔄 Inicializando ControlVersiones...");
            try {
                controlVersiones = new ControlVersiones(conexionBD);
                System.out.println("✅ ControlVersiones inicializado correctamente");
                System.out.println("   - ID Equipo: " + controlVersiones.getIdEquipo());
            } catch (Exception e) {
                System.err.println("❌ Error inicializando ControlVersiones: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.err.println("❌ conexionBD es null - no se puede inicializar ControlVersiones");
        }
            inicializarConexionYBuscarGeneral();       
            addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                    // Llamar a nuestro método de limpieza
                    cerrarConexiones();
                }
            });     

            label1.setCursor(new Cursor(Cursor.HAND_CURSOR));
            label1.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                togglePausaContador();
            }
        });
            //setVisible(true);//eliminar si quiero que salga la barra 

    }
    
    public static void cerrarTodoMenosActualizador() {
        try {
            String currentPid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                Runtime.getRuntime().exec("wmic process where \"(name='java.exe' or name='javaw.exe') and not processid=" + currentPid + " and not commandline like '%actualizador%'\" delete");
            } else {
                Runtime.getRuntime().exec("pkill -f java | grep -v " + currentPid + " | grep -v actualizador");
            }
        
            Thread.sleep(3000);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
       
        
    /**
    * Carga la configuración desde config.prop desencriptado
    * Si no existe, crea uno con valores por defecto o pide al usuario
    */
    private void cargarConfiguracion() {
        if (!fileExist()) {
            JOptionPane.showMessageDialog(null, 
                "❌ Error: No se encontró el archivo config.prop\n\n" +
                "El archivo de configuración es requerido.\n" +
                "Debe ser generado por el sistema principal.",
                "Archivo de Configuración No Encontrado", 
            JOptionPane.ERROR_MESSAGE);
            System.exit(0); // ✅ Cerrar inmediatamente
        } else {
            cargarPropiedadesDesencriptadas();
        }
    }


    private void cargarPropiedadesDesencriptadas() {
        try {
            Properties configFile = new Properties();
            configFile.load(new FileInputStream("config.prop"));
            
            // Desencriptar cada propiedad usando tu método existente
            srv = Utilerias.DecryptPassword(configFile.getProperty("SRV"));
            esquemaDb = Utilerias.DecryptPassword(configFile.getProperty("SDB"));
            userDb = Utilerias.DecryptPassword(configFile.getProperty("USR"));
            passDb = Utilerias.DecryptPassword(configFile.getProperty("PSS"));
            
            System.out.println("✅ Configuración cargada correctamente:");
            System.out.println("   - Servidor: " + srv);
            System.out.println("   - Esquema: " + esquemaDb);
            System.out.println("   - Usuario: " + userDb);
            // No imprimir password por seguridad
            
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, 
                "Error cargando configuración: " + e.getMessage(), 
                "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
    }


    //Verifica si existe el archivo config.prop 
    private boolean fileExist() {
        return new File("config.prop").exists();
    }
    
    
    private void inicializarConexionBD() {
        try {
            // Usar los datos desencriptados del config.prop
            conexionBD = new ConexionBD(
                srv,           // host desencriptado
                "3306",        // puerto
                esquemaDb,     // nombre BD desencriptado
                userDb,        // usuario desencriptado
                passDb         // password desencriptado
            );
            
            // Probar conexión en un hilo de fondo
            new Thread(() -> {
                if (conexionBD.probarConexion()) {
                    System.out.println("✅ MySQL conectado correctamente");
                    buscarRFCsEnBD(); // Buscar RFCs después de conexión exitosa
                } else {
                    System.err.println("❌ MySQL no disponible");
                    SwingUtilities.invokeLater(() -> {
                       // textArea2.setText("❌ Error: Conexión a la base de datos fallida.");
                    });
                }
            }).start();
            
        } catch (Exception e) {
            System.err.println("Error inicializando BD: " + e.getMessage());
            SwingUtilities.invokeLater(() -> {
                //textArea2.setText("❌ Error inicializando conexión a BD: " + e.getMessage());
            });
        }   
    }
    
    private void buscarRFCsEnBD() {
    String sql = "SELECT rfc FROM sucursales ORDER BY rfc ASC";
    
    try {
        java.sql.ResultSet rs = conexionBD.ejecutarConsulta(sql);
        
        // LIMPIAR la lista antes de llenarla
        listaRFCs.clear();
        
        while (rs.next()) {
            String rfc = rs.getString("rfc");
            listaRFCs.add(rfc); // ← AQUÍ SE LLENA LA LISTA
            System.out.println("✅ RFC encontrado: " + rfc); // Debug
        }
        
        System.out.println("📋 Total RFCs cargados: " + listaRFCs.size()); // Debug
        
        } catch (Exception e) {
            System.err.println("❌ Error buscando RFCs: " + e.getMessage());
            SwingUtilities.invokeLater(() -> {
                //textArea2.setText("❌ Error al ejecutar consulta de RFCs:\n" + e.getMessage());
            });
        }
    }
    
     
    private void inicializarConexionYBuscarGeneral() {
    try {
        String servidor = "74.208.11.142";
            String usuario = "actualizador";  
            String password = "actualiza09crimsoft25";
            String rutaBase = "./";  // ← SOLO directorio actual
    
        conexionSFTP = new conexion(servidor, 22, usuario, password, rutaBase);
    
        conexionSFTP.setConexionListener((conectado, mensaje) -> {
            //actualizarEstadoConexion(conectado, mensaje);
            
            // Cuando se conecte exitosamente, buscar actualizaciones generales
            if (conectado) {
                actualizarBarraProgreso(25, "Buscando actualizaciones...");
                buscarActualizacionesGenerales();
            }
        });
    
        // Conectar - cuando se conecte, el listener activará la búsqueda
        new Thread(() -> {
            try {
                conexionSFTP.conectar();
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    textArea1.setText("❌ Error conectando al servidor SFTP");
                });
            }
        }).start();
    
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void buscarActualizacionesGenerales() {
    // Actualizar estado en la interfaz
    SwingUtilities.invokeLater(() -> {
        label2.setText("🔍 BUSCANDO ACTUALIZACIONES GENERALES...");
        progressBar1.setValue(25);
        textArea1.setText("Conectando al servidor...");
    });
    
    // Ejecutar la lógica de búsqueda en un hilo de fondo
    new Thread(() -> {
        try {
            // 1. Usar la conexión SFTP ya inicializada y conectada
            if (conexionSFTP == null || !conexionSFTP.isConectado()) {
                SwingUtilities.invokeLater(() -> {
                    textArea1.setText("❌ Error: Conexión SFTP no disponible. Intente reconectar.");
                    progressBar1.setValue(0);
                    label2.setText("❌ ERROR DE CONEXIÓN");
                });
                return;
            }
            
            // 2. Listar archivos en carpeta "general/"
            SwingUtilities.invokeLater(() -> {
                textArea1.setText("Buscando archivos en carpeta actualizaciones/general/...");
            });
            
            // Se asume que el método listarArchivos está disponible y es síncrono
            Vector<ChannelSftp.LsEntry> archivos = conexionSFTP.listarArchivos("./actualizaciones/general/");
            
            // ✅ CORREGIDO: No detenerse si no hay archivos generales
            if (archivos.isEmpty() || archivos.stream().noneMatch(a -> a.getFilename().matches("ATC[GLR]_.*\\.zip"))) {
                SwingUtilities.invokeLater(() -> {
                    textArea1.setText("ℹ️ No se encontraron archivos de actualización válidos en la carpeta general/\n\n🔍 Continuando con actualizaciones por RFC...");
                    progressBar1.setValue(50);
                    label2.setText("🔍 CONTINUANDO CON RFCs");
                });
                
                // ✅ CORREGIDO: FORZAR QUE CONTINÚE CON LAS DESCARGAS
                SwingUtilities.invokeLater(() -> {
                    descargarActualizaciones();
                });
                return;
            }
            
            // 3. Filtrar y encontrar archivos más recientes por tipo
            String archivoGeneralMasReciente = null;
            String archivoLibreriasMasReciente = null;
            String archivoReportesMasReciente = null;
            
            for (ChannelSftp.LsEntry archivo : archivos) {
                String nombre = archivo.getFilename();
                
                if (nombre.startsWith("ATCG_") && nombre.endsWith(".zip")) {
                    archivoGeneralMasReciente = obtenerMasReciente(archivoGeneralMasReciente, nombre);
                } else if (nombre.startsWith("ATCL_") && nombre.endsWith(".zip")) {
                    archivoLibreriasMasReciente = obtenerMasReciente(archivoLibreriasMasReciente, nombre);
                } else if (nombre.startsWith("ATCR_") && nombre.endsWith(".zip")) {
                    archivoReportesMasReciente = obtenerMasReciente(archivoReportesMasReciente, nombre);
                }
            }
            
            // 4. Construir resultado para mostrar
            StringBuilder resultado = new StringBuilder();
            boolean hayActualizaciones = false;
            resultado.append("ACTUALIZACIONES GENERALES DISPONIBLES:\n\n");
            
            if (archivoGeneralMasReciente != null) {
                resultado.append("✅ SISTEMA PRINCIPAL:\n");
                resultado.append("    ").append(archivoGeneralMasReciente).append("\n\n");
                hayActualizaciones = true;
            }
            
            if (archivoLibreriasMasReciente != null) {
                resultado.append("✅ LIBRERÍAS:\n");
                resultado.append("    ").append(archivoLibreriasMasReciente).append("\n\n");
                hayActualizaciones = true;
            }
            
            if (archivoReportesMasReciente != null) {
                resultado.append("✅ REPORTES:\n");
                resultado.append("    ").append(archivoReportesMasReciente).append("\n\n");
                hayActualizaciones = true;
            }
            
            if (!hayActualizaciones) {
                resultado.append("ℹ️ No se encontraron archivos de actualización válidos\n\n🔍 Continuando con actualizaciones por RFC...");
            }
            
            // 5. Mostrar resultado en interfaz
            final String resultadoFinal = resultado.toString();
            
            SwingUtilities.invokeLater(() -> {
                textArea1.setText(resultadoFinal);
                actualizarBarraProgreso(100, "Búsqueda completada");
                label2.setText("✅ BÚSQUEDA GENERAL COMPLETADA");
                
                // ✅ CORREGIDO: SIEMPRE continuar con las descargas
                // Tanto si hay actualizaciones generales como si no
                descargarActualizaciones();
            });
            
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                textArea1.setText("❌ Error durante la búsqueda:\n" + e.getMessage() + "\n\n🔍 Intentando continuar con RFCs...");
                actualizarBarraProgreso(50, "Error - Continuando");
                label2.setText("⚠️  ERROR - CONTINUANDO");
                
                // ✅ CORREGIDO: Intentar continuar incluso con errores
                descargarActualizaciones();
            });
            }
        }).start();
    }
    
       
    private String obtenerMasReciente(String actual, String nuevo) {
    if (actual == null) return nuevo;
    
    // Extraer fecha y secuencia para comparar
    // Formato: ATCG_YYYYMMDD_NNN.zip
    try {
        String[] partesActual = actual.split("_");
        String[] partesNuevo = nuevo.split("_");
        
        if (partesActual.length >= 3 && partesNuevo.length >= 3) {
            String fechaActual = partesActual[1];
            String fechaNuevo = partesNuevo[1];
            
            // Comparar por fecha (YYYYMMDD)
            int comparacionFecha = fechaNuevo.compareTo(fechaActual);
            if (comparacionFecha > 0) {
                return nuevo;
            } else if (comparacionFecha == 0) {
                // Misma fecha, comparar por secuencia (NNN)
                String secuenciaActual = partesActual[2].replace(".zip", "");
                String secuenciaNuevo = partesNuevo[2].replace(".zip", "");
                
                    if (secuenciaNuevo.compareTo(secuenciaActual) > 0) {
                        return nuevo;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error comparando archivos: " + e.getMessage());
        }
    
        return actual;
    }
    
    private void descargarActualizaciones() {
    SwingUtilities.invokeLater(() -> {
        label2.setText("📥 DESCARGANDO ACTUALIZACIONES GENERALES Y ESPECÍFICAS...");
        progressBar1.setValue(0);
        textArea1.setText("Iniciando descarga de actualizaciones...");
    });
    
    new Thread(() -> {
        StringBuilder log = new StringBuilder();
        
        try {
            if (conexionSFTP == null || !conexionSFTP.isConectado()) {
                log.append("❌ Error: No hay conexión SFTP disponible\n");
                actualizarInterfazError(log.toString());
                return;
            }
            
            log.append("✅ Conexión SFTP establecida\n");
            actualizarBarraProgreso(20, "Descargando generales...");
            // ============================================
            // 1. DESCARGAR ACTUALIZACIONES GENERALES
            // ============================================
            log.append("\n🎯 INICIANDO ACTUALIZACIONES GENERALES\n");
            log.append("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n");
            
            // ✅ CORREGIDO: Agregar "GENERAL" como RFC
            int archivosGeneralesDescargados = descargarActualizacionesDeCarpeta(
                log, "./actualizaciones/general/", "GENERALES", "GENERAL");
            
            // ============================================
            // 2. DESCARGAR ACTUALIZACIONES POR RFC
            // ============================================
            log.append("\n🎯 INICIANDO ACTUALIZACIONES POR RFC\n");
            log.append("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n");
            actualizarBarraProgreso(50, "Descargando por RFC...");
            
            int archivosRfcDescargados = 0;
            
            // Obtener RFCs de la base de datos
            if (conexionBD != null && listaRFCs != null && !listaRFCs.isEmpty()) {
                log.append("📋 RFCs a procesar: ").append(listaRFCs.size()).append("\n\n");
                
                for (String rfc : listaRFCs) {
                    log.append("🔍 RFC: ").append(rfc).append("\n");
                    
                    String rutaCarpetaRFC = "./actualizaciones/" + rfc + "/";
                    // ✅ CORREGIDO: Pasar el RFC real
                    int descargadosEnRFC = descargarActualizacionesDeCarpeta(
                        log, rutaCarpetaRFC, "RFC " + rfc, rfc);
                    archivosRfcDescargados += descargadosEnRFC;
                    
                    if (descargadosEnRFC > 0) {
                        log.append("✅ ").append(rfc).append(": ").append(descargadosEnRFC).append(" archivos\n");
                    } else {
                        log.append("ℹ️ ").append(rfc).append(": Sin actualizaciones\n");
                    }
                    log.append("────────────────────────────────────────\n");
                }
            } else {
                log.append("ℹ️ No hay RFCs configurados\n");
            }
            
            // ============================================
            // 3. REGISTRAR EN BASE DE DATOS
            // ============================================
            if (controlVersiones != null) {
                //log.append("\n💾 GUARDANDO REGISTROS EN BASE DE DATOS...\n");
                
                // Registrar actualizaciones generales
                if (archivosGeneralesDescargados > 0) {
                    int nuevaVersionGeneral = obtenerVersionMasAltaDeCarpeta("./actualizaciones/general/");
                    controlVersiones.registrarActualizacion("GENERAL", nuevaVersionGeneral, archivosGeneralesDescargados);
                    log.append("✅ GENERALES: Versión ").append(nuevaVersionGeneral).append(" registrada\n");
                }
                
                // Registrar actualizaciones por RFC
                if (archivosRfcDescargados > 0 && listaRFCs != null) {
                    for (String rfc : listaRFCs) {
                        int nuevaVersionRFC = obtenerVersionMasAltaDeCarpeta("./actualizaciones/" + rfc + "/");
                        if (nuevaVersionRFC > 0) {
                            controlVersiones.registrarActualizacion(rfc, nuevaVersionRFC, 1);
                            log.append("✅ ").append(rfc).append(": Versión ").append(nuevaVersionRFC).append(" registrada\n");
                        }
                    }
                }
            }
            
            // ============================================
            // 4. RESUMEN FINAL
            // ============================================
            log.append("\n🎉 PROCESO COMPLETADO\n");
            log.append("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n");
            actualizarBarraProgreso(100, "Descarga completada");
            log.append("📦 Actualizaciones generales: ").append(archivosGeneralesDescargados).append(" archivos\n");
            log.append("📊 Actualizaciones por RFC: ").append(archivosRfcDescargados).append(" archivos\n");
            log.append("📈 Total: ").append(archivosGeneralesDescargados + archivosRfcDescargados).append(" archivos descargados\n");
            
            final String resultadoFinal = log.toString();
            SwingUtilities.invokeLater(() -> {
                //mostrarLineaPorLinea(textArea1, resultadoFinal);
                textArea1.setText(resultadoFinal);
                progressBar1.setValue(100);
                label2.setText("✅ DESCARGA FINALIZADA");
                JOptionPane.showMessageDialog(this, "✅ Actualización completada exitosamente", "Actualización Exitosa", JOptionPane.INFORMATION_MESSAGE);
                //cerrarConCuentaRegresiva();
                iniciarContadorInteractivo();
            });
            
        } catch (Exception e) {
            log.append("❌ ERROR: ").append(e.getMessage()).append("\n");
            actualizarInterfazError(log.toString());
            actualizarBarraProgreso(0, "Error en descarga");
        }
        }).start();
    }

    /**
     * Método auxiliar para descargar actualizaciones de una carpeta específica
     * @param log StringBuilder para registrar el proceso
     * @param rutaCarpeta Ruta en el servidor SFTP (ej: "./actualizaciones/general/" o "./actualizaciones/RFC/")
     * @param tipoDescarga Etiqueta para el log (ej: "GENERALES", "RFC ABC123456")
     * @return Número de archivos descargados exitosamente
     */

    /**
     * Método auxiliar para descargar actualizaciones de una carpeta específica
     */
    private int descargarActualizacionesDeCarpeta(StringBuilder log, String rutaCarpeta, 
                                                  String tipoDescarga, String rfcCliente) {
        int archivosDescargados = 0;

        try {
            // Obtener lista de archivos disponibles en la carpeta (YA ORDENADOS)
            Vector<ChannelSftp.LsEntry> archivos = conexionSFTP.listarArchivos(rutaCarpeta);

            if (archivos == null || archivos.isEmpty()) {
                return 0;
            }

            // ✅ NUEVA LÓGICA: Obtener último archivo descargado
            String ultimoArchivoDescargado = null;
            if (controlVersiones != null && rfcCliente != null) {
                ultimoArchivoDescargado = controlVersiones.obtenerUltimoArchivoDescargado(rfcCliente);
                if (ultimoArchivoDescargado != null) {
                    log.append("   📄 Último archivo descargado: ").append(ultimoArchivoDescargado).append("\n");
                } else {
                    log.append("   📄 No hay registros previos - Descargando todo\n");
                }
            }

            int totalArchivosValidos = 0;

            // Contar archivos válidos para descargar
            for (ChannelSftp.LsEntry archivo : archivos) {
                String nombre = archivo.getFilename();
                if (nombre.matches("ATC[GLR]_.*\\.zip")) {
                    totalArchivosValidos++;
                }
            }

            log.append("   🎯 Archivos encontrados: ").append(totalArchivosValidos).append("\n");

            if (totalArchivosValidos == 0) {
                return 0;
            }

            // Descargar cada archivo válido
            for (ChannelSftp.LsEntry archivo : archivos) {
                String nombreArchivo = archivo.getFilename();

                if (nombreArchivo.matches("ATC[GLR]_.*\\.zip")) {

                    // ✅ NUEVA LÓGICA PRINCIPAL: Solo descargar desde el último archivo descargado
                    boolean descargarEsteArchivo = debeDescargarDesdeUltimo(nombreArchivo, ultimoArchivoDescargado, archivos);
                    if (!descargarEsteArchivo) {
                        log.append("   ⏭️  ").append(nombreArchivo).append(" - Ya descargado (historial)\n");
                        continue;
                    }

                    // ✅ VERIFICACIÓN POR ARCHIVO ESPECÍFICO (por si acaso)
                    if (controlVersiones != null && rfcCliente != null) {
                        boolean yaDescargado = controlVersiones.yaDescargado(rfcCliente, nombreArchivo);
                        if (yaDescargado) {
                            log.append("   ⏭️  ").append(nombreArchivo).append(" - Ya descargado anteriormente\n");
                            continue;
                        }
                    }

                    // Determinar carpeta destino
                    String carpetaDestino;
                    if (nombreArchivo.contains("ATCG")) {
                        carpetaDestino = "."; // Carpeta raíz
                    } else if (nombreArchivo.contains("ATCL")) {
                        carpetaDestino = "lib";
                    } else if (nombreArchivo.contains("ATCR")) {
                        carpetaDestino = "reportes";
                    } else {
                        continue;
                    }

                    log.append("\n   📦 ").append(nombreArchivo).append("\n");

                    // Crear carpeta destino si no existe
                    File carpeta = new File(carpetaDestino);
                    if (!carpeta.exists()) {
                        carpeta.mkdirs();
                    }

                    // Ruta temporal para descargar ZIP
                    String rutaTemporal = System.getProperty("java.io.tmpdir") + File.separator + nombreArchivo;

                    // Descargar archivo
                    String rutaRemota = rutaCarpeta + nombreArchivo;
                    boolean descargado = conexionSFTP.descargarArchivo(rutaRemota, rutaTemporal);

                    if (descargado) {
                        log.append("   ✅ Descargado\n");

                        // Extraer ZIP a carpeta destino
                        log.append("   📦 Extrayendo...\n");
                        boolean extraido = extraerZip(rutaTemporal, carpetaDestino);

                        if (extraido) {
                            archivosDescargados++;
                            log.append("   ✅ Completado\n");

                            // REGISTRAR ARCHIVO DESCARGADO EN BD
                            if (controlVersiones != null && rfcCliente != null) {
                                controlVersiones.registrarArchivoDescargado(rfcCliente, nombreArchivo);
                            }
                        } else {
                            log.append("   ❌ Error en extracción\n");
                        }

                    } else {
                        log.append("   ❌ Falló descarga\n");
                    }

                    // Actualizar progreso general
                    actualizarInterfazProgreso(log.toString(), archivosDescargados, totalArchivosValidos);
                }
            }

            log.append("   ✅ ").append(tipoDescarga).append(": ").append(archivosDescargados)
               .append("/").append(totalArchivosValidos).append(" archivos\n");

        } catch (Exception e) {
            log.append("   ❌ Error: ").append(e.getMessage()).append("\n");
        }

        return archivosDescargados;
    }



    //borrar hasta el metodo de es archivo mas receiente con contro z si falla 

    private int obtenerVersionMasAltaDeCarpeta(String rutaCarpeta) {
        try {
            Vector<ChannelSftp.LsEntry> archivos = conexionSFTP.listarArchivos(rutaCarpeta);
            int versionMasAlta = 0;
            for (ChannelSftp.LsEntry archivo : archivos) {
                String nombre = archivo.getFilename();
                if (nombre.matches("ATC[GLR]_.*\\.zip")) {
                    int version = extraerVersionArchivo(nombre);
                    if (version > versionMasAlta) {
                        versionMasAlta = version;
                    }
                }
            }
            return versionMasAlta;
        } catch (Exception e) {
            return 0;
        }
    }
    private int extraerVersionArchivo(String nombreArchivo) {
        try {
            // Buscar el patrón _NNN.zip al final
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("_(\\d+)\\.zip$");
            java.util.regex.Matcher matcher = pattern.matcher(nombreArchivo);

            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) {
            System.err.println("Error extrayendo versión de: " + nombreArchivo);
        }
        return 0; // Si no se puede extraer, asumir versión 0
    }

    /**
     * Determina si un archivo es el más reciente de su tipo
     * Sin afectar la lógica existente de control de versiones
     */
    private boolean esArchivoMasReciente(String nombreArchivo, Vector<ChannelSftp.LsEntry> archivos) {
        try {
            String tipo = nombreArchivo.substring(0, 4); // "ATCG", "ATCL", "ATCR"
            String fechaArchivo = nombreArchivo.split("_")[1];

            // Buscar el archivo más reciente del mismo tipo
            String fechaMasReciente = fechaArchivo;

            for (ChannelSftp.LsEntry archivo : archivos) {
                String nombreOtro = archivo.getFilename();
                if (nombreOtro.startsWith(tipo) && nombreOtro.matches("ATC[GLR]_.*\\.zip")) {
                    String fechaOtro = nombreOtro.split("_")[1];
                    if (fechaOtro.compareTo(fechaMasReciente) > 0) {
                        fechaMasReciente = fechaOtro;
                    }
                }
            }

            // Este archivo es el más reciente si su fecha es igual a la más reciente encontrada
            return fechaArchivo.equals(fechaMasReciente);

        } catch (Exception e) {
            System.err.println("❌ Error verificando archivo más reciente: " + nombreArchivo);
            return true; // Por defecto, procesar el archivo
        }
    }




    /**
     * Determina si un archivo debe descargarse basado en el último archivo descargado
     * Los archivos vienen ordenados naturalmente por nombre (fecha + secuencia)
     */
    private boolean debeDescargarDesdeUltimo(String nombreArchivo, String ultimoArchivoDescargado, 
                                            Vector<ChannelSftp.LsEntry> archivos) {
        if (ultimoArchivoDescargado == null) {
            return true; // No hay registro previo, descargar todo
        }

        // Buscar el último archivo descargado en la lista
        boolean encontradoUltimo = false;

        for (ChannelSftp.LsEntry archivo : archivos) {
            String nombre = archivo.getFilename();

            // Cuando encontramos el último archivo descargado
            if (nombre.equals(ultimoArchivoDescargado)) {
                encontradoUltimo = true;
                continue; // Este ya está descargado, saltarlo
            }

            // Todos los archivos DESPUÉS del último descargado
            if (encontradoUltimo) {
                // Si este es el archivo que estamos evaluando, descargarlo
                if (nombre.equals(nombreArchivo)) {
                    return true;
                }
            }
        }

        // Si llegamos aquí, puede ser que:
        // - No encontramos el último archivo (fue eliminado del servidor)
        // - Este archivo está antes del último descargado
        // Por seguridad, descargar solo si no encontramos el último
        return !encontradoUltimo;
    }


    //si llegamos hasta aqui con cotrol z es que ya funciona el cierre por conteo con pausa y pasamos a los metodos de forzar descargas 

    /**
     * Descarga TODAS las actualizaciones ignorando el control de versiones
     * Útil para equipos muy desactualizados o con problemas en BD
     */
    private void descargarTodoForzado() {
        SwingUtilities.invokeLater(() -> {
            label2.setText("🔧 FORZANDO DESCARGAS COMPLETAS...");
            progressBar1.setValue(0);
            textArea1.setText("Iniciando descargas forzadas...");
        });

        new Thread(() -> {
            StringBuilder log = new StringBuilder();

            try {
                if (conexionSFTP == null || !conexionSFTP.isConectado()) {
                    log.append("❌ Error: No hay conexión SFTP disponible\n");
                    actualizarInterfazError(log.toString());
                    return;
                }

                log.append("✅ Conexión SFTP establecida\n");
                log.append("🔧 MODO: Descargas forzadas (ignorando control de versiones)\n");

                // ============================================
                // 1. DESCARGAR ACTUALIZACIONES GENERALES (FORZADO)
                // ============================================
                log.append("\n🎯 DESCARGANDO TODAS LAS GENERALES\n");
                log.append("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n");

                int archivosGeneralesDescargados = descargarActualizacionesDeCarpetaForzado(
                    log, "./actualizaciones/general/", "GENERALES", "GENERAL");

                // ============================================
                // 2. DESCARGAR ACTUALIZACIONES POR RFC (FORZADO)
                // ============================================
                log.append("\n🎯 DESCARGANDO TODOS LOS RFC\n");
                log.append("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n");

                int archivosRfcDescargados = 0;

                if (conexionBD != null && listaRFCs != null && !listaRFCs.isEmpty()) {
                    log.append("📋 RFCs a procesar: ").append(listaRFCs.size()).append("\n\n");

                    for (String rfc : listaRFCs) {
                        log.append("🔍 RFC: ").append(rfc).append("\n");

                        String rutaCarpetaRFC = "./actualizaciones/" + rfc + "/";
                        int descargadosEnRFC = descargarActualizacionesDeCarpetaForzado(
                            log, rutaCarpetaRFC, "RFC " + rfc, rfc);
                        archivosRfcDescargados += descargadosEnRFC;

                        log.append("✅ ").append(rfc).append(": ").append(descargadosEnRFC).append(" archivos\n");
                        log.append("────────────────────────────────────────\n");
                    }
                }

                // ============================================
                // 3. RESUMEN FINAL
                // ============================================
                log.append("\n🎉 DESCARGA FORZADA COMPLETADA\n");
                log.append("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n");
                log.append("📦 Actualizaciones generales: ").append(archivosGeneralesDescargados).append(" archivos\n");
                log.append("📊 Actualizaciones por RFC: ").append(archivosRfcDescargados).append(" archivos\n");
                log.append("📈 Total: ").append(archivosGeneralesDescargados + archivosRfcDescargados).append(" archivos descargados\n");
                log.append("💡 Nota: Se ignoró el control de versiones\n");

                final String resultadoFinal = log.toString();
                SwingUtilities.invokeLater(() -> {
                    mostrarLineaPorLinea(textArea1, resultadoFinal);
                    progressBar1.setValue(100);
                    label2.setText("✅ DESCARGA FORZADA COMPLETADA");

                    // Iniciar contador interactivo
                    iniciarContadorInteractivo();
                });

            } catch (Exception e) {
                log.append("❌ ERROR: ").append(e.getMessage()).append("\n");
                actualizarInterfazError(log.toString());
            }
        }).start();
    }


    /**
     * Versión forzada que ignora completamente el control de versiones
     */
    private int descargarActualizacionesDeCarpetaForzado(StringBuilder log, String rutaCarpeta, 
                                                         String tipoDescarga, String rfcCliente) {
        int archivosDescargados = 0;

        try {
            log.append("🔧 MODO FORZADO - Descargando todo...\n");

            // Obtener lista de archivos disponibles
            Vector<ChannelSftp.LsEntry> archivos = conexionSFTP.listarArchivos(rutaCarpeta);

            if (archivos == null || archivos.isEmpty()) {
                log.append("   ℹ️ No hay archivos en esta carpeta\n");
                return 0;
            }

            int totalArchivosValidos = 0;

            // Contar archivos válidos
            for (ChannelSftp.LsEntry archivo : archivos) {
                String nombre = archivo.getFilename();
                if (nombre.matches("ATC[GLR]_.*\\.zip")) {
                    totalArchivosValidos++;
                }
            }

            log.append("   🎯 Archivos a descargar: ").append(totalArchivosValidos).append("\n");

            if (totalArchivosValidos == 0) {
                return 0;
            }

            // Descargar CADA archivo válido (sin verificaciones)
            for (ChannelSftp.LsEntry archivo : archivos) {
                String nombreArchivo = archivo.getFilename();

                if (nombreArchivo.matches("ATC[GLR]_.*\\.zip")) {

                    log.append("\n   📦 ").append(nombreArchivo).append(" 🔧\n");

                    // Determinar carpeta destino
                    String carpetaDestino;
                    if (nombreArchivo.contains("ATCG")) {
                        carpetaDestino = ".";
                    } else if (nombreArchivo.contains("ATCL")) {
                        carpetaDestino = "lib";
                    } else if (nombreArchivo.contains("ATCR")) {
                        carpetaDestino = "reportes";
                    } else {
                        continue;
                    }

                    // Crear carpeta destino
                    File carpeta = new File(carpetaDestino);
                    if (!carpeta.exists()) {
                        carpeta.mkdirs();
                    }

                    // Ruta temporal
                    String rutaTemporal = System.getProperty("java.io.tmpdir") + File.separator + nombreArchivo;

                    // Descargar archivo
                    String rutaRemota = rutaCarpeta + nombreArchivo;
                    boolean descargado = conexionSFTP.descargarArchivo(rutaRemota, rutaTemporal);

                    if (descargado) {
                        log.append("   ✅ Descargado\n");

                        // Extraer ZIP
                        log.append("   📦 Extrayendo...\n");
                        boolean extraido = extraerZip(rutaTemporal, carpetaDestino);

                        if (extraido) {
                            archivosDescargados++;
                            log.append("   ✅ Completado\n");

                            // Registrar en BD (opcional)
                            if (controlVersiones != null && rfcCliente != null) {
                                controlVersiones.registrarArchivoDescargado(rfcCliente, nombreArchivo);
                            }
                        } else {
                            log.append("   ❌ Error en extracción\n");
                        }

                    } else {
                        log.append("   ❌ Falló descarga\n");
                    }

                    actualizarInterfazProgreso(log.toString(), archivosDescargados, totalArchivosValidos);
                }
            }

            log.append("   ✅ ").append(tipoDescarga).append(": ").append(archivosDescargados)
               .append("/").append(totalArchivosValidos).append(" archivos\n");

        } catch (Exception e) {
            log.append("   ❌ Error: ").append(e.getMessage()).append("\n");
        }

        return archivosDescargados;
    }


    private void configurarTeclaF7() {
        // Crear acción para F7
        Action forzarDescargasAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                activarModoForzado();
            }
        };

        // Crear el key binding para F7
        String key = "FORZAR_DESCARGAS";
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0), key);
        getRootPane().getActionMap().put(key, forzarDescargasAction);
    }

    /**
     * Activa el modo forzado con confirmación (F7)
     */
    private void activarModoForzado() {
        int confirmacion = JOptionPane.showConfirmDialog(
            this, 
            "¿Activar MODO FORZADO de descargas?\n" +
            "• Descargará TODAS las actualizaciones\n" + 
            "• Ignorará el control de versiones\n" +
            "• Útil para equipos desactualizados\n\n" +
            "¿Continuar?",
            "🔧 Modo Forzado - F7",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (confirmacion == JOptionPane.YES_OPTION) {
            // Mostrar mensaje de activación
            JOptionPane.showMessageDialog(this,
                "🔄 Modo forzado activado\n" +
                "Iniciando descarga de TODAS las actualizaciones...",
                "Modo Forzado Activado",
                JOptionPane.INFORMATION_MESSAGE);

            // Ejecutar descargas forzadas
            descargarTodoForzado();
        }
    }

    private void actualizarInterfazProgreso(String mensaje, int descargados, int total) {
        SwingUtilities.invokeLater(() -> {
            textArea1.setText(mensaje);
            int progreso = total > 0 ? (int) ((descargados / (double) total) * 100) : 0;
            progressBar1.setValue(progreso);
        });
    }

    private void actualizarInterfazError(String mensaje) {
        SwingUtilities.invokeLater(() -> {
            textArea1.setText(mensaje);
            progressBar1.setValue(0);
            label2.setText("❌ ERROR");
        });
    }
    
    
    private boolean extraerZip(String rutaZip, String carpetaDestino) {
    try {
        java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(rutaZip);
        java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
        
        while (entries.hasMoreElements()) {
            java.util.zip.ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory()) {
                File archivoDestino = new File(carpetaDestino, entry.getName());
                
                // Crear directorios padres si es necesario
                archivoDestino.getParentFile().mkdirs();
                
                try (java.io.InputStream is = zipFile.getInputStream(entry);
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(archivoDestino)) {
                    
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = is.read(buffer)) > 0) {
                        fos.write(buffer, 0, length);
                    }
                }
            }
        }
        
        zipFile.close();
            return true;
        
        } catch (Exception e) {
            System.err.println("❌ Error extrayendo ZIP: " + e.getMessage());
            return false;
        }
    }
       
    
    
    
    private void mostrarLineaPorLinea(JTextArea textArea, String texto) {
        new Thread(() -> {
            String[] lineas = texto.split("\n");
            StringBuilder contenido = new StringBuilder();

            for (String linea : lineas) {
                contenido.append(linea).append("\n");
                SwingUtilities.invokeLater(() -> {
                    textArea.setText(contenido.toString());
                    textArea.setCaretPosition(textArea.getDocument().getLength());
                });
                try {
                    Thread.sleep(100); // Pausa entre líneas
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }
   
  

    /**
     * Contador interactivo que se puede pausar/reanudar con clic
     */
    private void iniciarContadorInteractivo() {
        segundosRestantes = 5; // Reiniciar contador
        contadorPausado = false;

        hiloContador = new Thread(() -> {
            try {
                while (segundosRestantes > 0) {
                    // Si está pausado, esperar sin contar
                    if (contadorPausado) {
                        Thread.sleep(100);
                        continue;
                    }

                    final int segundosActuales = segundosRestantes;
                    SwingUtilities.invokeLater(() -> {
                        label1.setText("⏸️ Cierre en " + segundosActuales + "s");
                    });

                    Thread.sleep(1000); // Esperar 1 segundo
                    segundosRestantes--;
                }

                // Si llegó a cero y no está pausado, cerrar
                if (!contadorPausado) {
                    SwingUtilities.invokeLater(() -> {
                        System.out.println("🔒 Cerrando aplicación automáticamente...");
                        cerrarConexiones();
                    });
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        hiloContador.start();
    }


    /**
     * Maneja el clic en el label para pausar/reanudar
     */
    private void togglePausaContador() {
        contadorPausado = !contadorPausado;

        if (contadorPausado) {
            label1.setText("⏸️ Cierre PAUSADO ");
        } else {
            label1.setText("▶️ Cierre en " + segundosRestantes + "s");
        }
    }

    private void actualizarBarraProgreso(int valor, String texto) {
        SwingUtilities.invokeLater(() -> {
            progressBar1.setValue(valor);
            progressBar1.setString(texto);
        });
    }

    private void configurarInterfaz() {
        // Restauramos el texto predeterminado. FlatLaf debe manejar el color del texto.

        // 1. Configurar Títulos y Versión
        jblTitulo.setText("ACTUALIZADOR");
        // Eliminamos la configuración de color manual para que FlatLaf determine el contraste
        jblTitulo.setFont(jblTitulo.getFont().deriveFont(jblTitulo.getFont().getStyle() | Font.BOLD, jblTitulo.getFont().getSize() + 6f));

        jblVersion.setText("Versión: 1.2.0");
        // Eliminamos la configuración de color manual.

        label2.setText("🔍 INICIANDO BÚSQUEDA DE ACTUALIZACIONES...");
        // Eliminamos la configuración de color manual.

        // 2. Configurar Barra de Progreso
        progressBar1.setStringPainted(true);
        progressBar1.setString("Iniciando...");//si llegamos aqui con control z estamos en la parte donde ya cierra todo y quiero mejorar la interfaz 
        // Eliminamos la configuración de color manual.

        // 3. Configurar Paneles y Áreas de Texto (Restaurando y manteniendo solo ajustes funcionales)

        // Restauramos bordes con títulos simples (FlatLaf se encarga del estilo)
        panel2.setBorder(BorderFactory.createTitledBorder("📦 ACTUALIZACIONES GENERALES"));

        textArea1.setText("Iniciando la secuencia de conexión y búsqueda de archivos...");


        textArea1.setEditable(false);


        // Mantenemos el ajuste de línea (LineWrap) para evitar el descuadre horizontal
        textArea1.setLineWrap(true);    


        // Eliminamos todas las configuraciones de color manuales (setForeground, setBackground)
        // para permitir que FlatLaf lo maneje correctamente.

        // 4. Configurar Resumen y Botones


        // 5. Configurar la Ventana y el Listener de Cierre (MANTENER ESTO ES VITAL)
        setTitle("Actualizador v1.2.0");

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE); 

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                cerrarConexiones();
            }
        });
    }
    

  private void hacerVentanaNoCerrable() {
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        getRootPane().setWindowDecorationStyle(JRootPane.NONE);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                // ✅ PRIMERO el contador
                try { 
                    Thread.sleep(300000); // 5 segundos de espera
                } catch (InterruptedException e) { }

                // ✅ LUEGO el mensaje
                JOptionPane.showMessageDialog(ActualizadorPR.this,
                    "⏳ Espere a que el proceso termine...\n" +
                    "La aplicación se cerrará automáticamente.",
                    "Cierre No Permitido",
                    JOptionPane.INFORMATION_MESSAGE);
            }
        });
    }
    
    
    
    private void cerrarConexiones() {
        System.out.println("Cerrando conexiones...");

        // Cerrar conexión SFTP
        if (conexionSFTP != null) {
            conexionSFTP.desconectar(); // Asumiendo que su clase 'conexion' tiene un método desconectar()
            System.out.println("Conexión SFTP cerrada.");
        }

        // Cerrar conexión a la Base de Datos
        if (conexionBD != null) {
            conexionBD.desconectar(); // Asumiendo que su clase 'ConexionBD' tiene un método cerrarConexion()
            System.out.println("Conexión BD cerrada.");
        }

        // Finalizar la aplicación
        System.exit(0);
    }
    
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    // Generated using JFormDesigner Educational license - Juan Padilla (JUAN ALEJANDRO CASTRO PADILLA)
    private void initComponents() {
	panel1 = new JPanel();
	jblTitulo = new JLabel();
	jblVersion = new JLabel();
	label2 = new JLabel();
	progressBar1 = new JProgressBar();
	panel2 = new JPanel();
	scrollPane1 = new JScrollPane();
	textArea1 = new JTextArea();
	label1 = new JLabel();

	//======== this ========
	setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
	setMinimumSize(new Dimension(500, 600));
	setPreferredSize(new Dimension(500, 600));
	setResizable(false);
	var contentPane = getContentPane();
	contentPane.setLayout(new MigLayout(
	    "insets 0,hidemode 3",
	    // columns
	    "[100,fill]" +
	    "[100,fill]" +
	    "[100,fill]" +
	    "[100,fill]" +
	    "[100,fill]",
	    // rows
	    "[fill]" +
	    "[]" +
	    "[]" +
	    "[]" +
	    "[]" +
	    "[]" +
	    "[]"));

	//======== panel1 ========
	{
	    panel1.setLayout(new MigLayout(
		"hidemode 3",
		// columns
		"[250,fill]" +
		"[250,fill]",
		// rows
		"[]" +
		"[]"));

	    //---- jblTitulo ----
	    jblTitulo.setText("ACTUALIZADOR");
	    jblTitulo.setFont(jblTitulo.getFont().deriveFont(jblTitulo.getFont().getStyle() | Font.BOLD, jblTitulo.getFont().getSize() + 3f));
	    jblTitulo.setHorizontalAlignment(SwingConstants.CENTER);
	    panel1.add(jblTitulo, "cell 0 0 2 1");

	    //---- jblVersion ----
	    jblVersion.setText("text");
	    jblVersion.setHorizontalAlignment(SwingConstants.CENTER);
	    panel1.add(jblVersion, "cell 0 1 2 1");
	}
	contentPane.add(panel1, "cell 0 0 5 1");

	//---- label2 ----
	label2.setText("text");
	contentPane.add(label2, "cell 0 1 4 1");

	//---- progressBar1 ----
	progressBar1.setMinimumSize(new Dimension(400, 20));
	progressBar1.setMaximumSize(new Dimension(500, 20));
	contentPane.add(progressBar1, "cell 0 2 5 1");

	//======== panel2 ========
	{
	    panel2.setMinimumSize(new Dimension(34, 100));
	    panel2.setPreferredSize(new Dimension(500, 412));
	    panel2.setLayout(new MigLayout(
		"hidemode 3",
		// columns
		"[500,fill]",
		// rows
		"[]"));

	    //======== scrollPane1 ========
	    {
		scrollPane1.setMinimumSize(new Dimension(22, 100));
		scrollPane1.setPreferredSize(new Dimension(450, 400));

		//---- textArea1 ----
		textArea1.setMinimumSize(new Dimension(500, 100));
		textArea1.setEditable(false);
		textArea1.setFont(textArea1.getFont().deriveFont(textArea1.getFont().getStyle() & ~Font.BOLD, textArea1.getFont().getSize() + 2f));
		scrollPane1.setViewportView(textArea1);
	    }
	    panel2.add(scrollPane1, "cell 0 0");
	}
	contentPane.add(panel2, "cell 0 3 5 1");

	//---- label1 ----
	label1.setText("\u23f3");
	contentPane.add(label1, "cell 0 4 3 1");
	pack();
	setLocationRelativeTo(getOwner());
    }// </editor-fold>//GEN-END:initComponents

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
        // *** 🔑 CÓDIGO A AÑADIR/REEMPLAZAR PARA TEMA OSCURO 🔑 ***
        
        // Cargar el Look and Feel oscuro de FlatLaf
        // Necesitas haber agregado la librería FlatLaf a tu proyecto
        com.formdev.flatlaf.FlatDarkLaf.setup();
        
        // *** FIN CÓDIGO DE TEMA OSCURO ***
        
    } catch (Exception ex) {
        // En caso de error, puedes volver al Look and Feel del sistema
        try {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
             java.util.logging.Logger.getLogger(ActualizadorPR.class.getName()).log(java.util.logging.Level.SEVERE, null, e);
        }
    }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new ActualizadorPR().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // Generated using JFormDesigner Educational license - Juan Padilla (JUAN ALEJANDRO CASTRO PADILLA)
    private JPanel panel1;
    private JLabel jblTitulo;
    private JLabel jblVersion;
    private JLabel label2;
    private JProgressBar progressBar1;
    private JPanel panel2;
    private JScrollPane scrollPane1;
    private JTextArea textArea1;
    private JLabel label1;
    // End of variables declaration//GEN-END:variables
}
