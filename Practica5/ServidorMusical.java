/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Practica5;

/**
 *
 * @author Lenovo
 */
import java.io.*;
import java.net.*;
import java.util.*;

public class ServidorMusical {

    private int puerto;
    private String rutaCarpeta;
    private String idServidor;

    public ServidorMusical(int puerto, String rutaCarpeta, String idServidor) {
        this.puerto = puerto;
        this.rutaCarpeta = rutaCarpeta;
        this.idServidor = idServidor;
    }

    public void iniciar() {
        try (ServerSocket serverSocket = new ServerSocket(puerto)) {
            System.out.println("=== " + idServidor + " ACTIVO en puerto " + puerto + " ===");
            System.out.println(">>> Directorio de música: " + rutaCarpeta);

            while (true) {
                Socket cliente = serverSocket.accept();
                new Thread(new ManejadorCanciones(cliente)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ManejadorCanciones implements Runnable {
        private Socket socket;

        public ManejadorCanciones(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                // 1. Recibir la petición (Nombre de la canción EN BITS)
                String nombreBuscadoBits = in.readUTF();
                System.out.println(idServidor + " recibió consulta en bits: " + nombreBuscadoBits);

                File carpeta = new File(rutaCarpeta);
                File[] archivos = carpeta.listFiles();
                File archivoEncontrado = null;

                if (archivos != null) {
                    for (File f : archivos) {
                        if (f.isFile()) {
                            // Convertir nombre del archivo real a bits para comparar
                            String nombreArchivoBits = Conversion.textoABits(f.getName());

                            if (nombreArchivoBits.equals(nombreBuscadoBits)) {
                                archivoEncontrado = f;
                                break;
                            }
                        }
                    }
                }

                // 3. Responder al cliente
                if (archivoEncontrado != null) {
                    System.out.println(">>> ¡CANCION ENCONTRADA! Enviando...");
                    out.writeBoolean(true); // Decimos SI la tenemos

                    // Enviar el archivo
                    long tamano = archivoEncontrado.length();
                    out.writeLong(tamano);

                    FileInputStream fis = new FileInputStream(archivoEncontrado);
                    byte[] buffer = new byte[4096];
                    int leidos;
                    while ((leidos = fis.read(buffer)) != -1) {
                        out.write(buffer, 0, leidos);
                    }
                    fis.close();
                    System.out.println(">>> Transferencia completada.");

                } else {
                    System.out.println(">>> Cancion no encontrada aquí.");
                    out.writeBoolean(false); // Decimos NO la tenemos
                }

                out.flush();
                socket.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // MAIN PARA EJECUTAR LOS 3 SERVIDORES
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("¿Qué servidor deseas iniciar? (1, 2 o 3): ");
        int opcion = sc.nextInt();

        // configurar las carpetas
        String ruta1 = "C:\\servidores_musica\\server1";
        String ruta2 = "C:\\servidores_musica\\server2";
        String ruta3 = "C:\\servidores_musica\\server3";

        switch (opcion) {
            case 1:
                new ServidorMusical(9001, ruta1, "SERVIDOR 1").iniciar();
                break;
            case 2:
                new ServidorMusical(9002, ruta2, "SERVIDOR 2").iniciar();
                break;
            case 3:
                new ServidorMusical(9003, ruta3, "SERVIDOR 3").iniciar();
                break;
            default:
                System.out.println("Opción no válida");
        }
    }
}