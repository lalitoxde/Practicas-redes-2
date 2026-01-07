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
import java.util.Scanner;

public class ClienteSpotify {

    private static final int[] PUERTOS = { 9001, 9002, 9003 };
    private static final String HOST = "localhost";

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.println("=== BIENVENIDO A SPOTIFY DISTRIBUIDO (BIT SEARCH) ===");
        System.out.print("Ingrese el nombre EXACTO de la cancion (con extension, ej: rock.mp3): ");
        String nombreCancion = sc.nextLine();

        String busquedaEnBits = Conversion.textoABits(nombreCancion);
        System.out.println("\n[Procesando] Convirtiendo '" + nombreCancion + "' a flujo de bits...");
        System.out.println("[Bits] " + busquedaEnBits);
        System.out.println("-------------------------------------------------------");

        boolean encontrada = false;

        // 2. Iterar sobre los servidores (Busqueda secuencial)
        for (int puerto : PUERTOS) {
            System.out.println(">>> Preguntando al Servidor en puerto " + puerto + "...");

            if (buscarYReproducir(puerto, busquedaEnBits, nombreCancion)) {
                encontrada = true;
                break; // Rompemos el ciclo porque ya la encontramos
            }
        }

        if (!encontrada) {
            System.out.println("\n=== ERROR: La canción no está en ningún servidor disponible. ===");
        }
    }

    private static boolean buscarYReproducir(int puerto, String nombreBits, String nombreOriginal) {
        try (Socket socket = new Socket(HOST, puerto)) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // 1. Enviar petición
            out.writeUTF(nombreBits);
            out.flush();

            boolean existe = in.readBoolean();

            if (existe) {
                System.out.println(">>> ¡EXITO! Servidor " + puerto + " tiene la canción.");
                System.out.println(">>> Buffering (Descargando para reproducir)...");

                long tamano = in.readLong();
                byte[] buffer = new byte[4096];
                int leidos;
                long totalLeido = 0;

                String nombreArchivoFinal = "reproducir_" + nombreOriginal;
                File archivoMusica = new File(nombreArchivoFinal);

                FileOutputStream fos = new FileOutputStream(archivoMusica);

                InputStream sIn = socket.getInputStream();
                while (totalLeido < tamano && (leidos = sIn.read(buffer)) != -1) {
                    fos.write(buffer, 0, leidos);
                    totalLeido += leidos;
                }
                fos.close();

                System.out.println(">>> ¡Listo! Reproduciendo: " + nombreOriginal);

                // --- NUEVO: REPRODUCIR AUTOMÁTICAMENTE ---
                try {
                    // Verificamos si el sistema soporta abrir archivos (Desktop API)
                    if (java.awt.Desktop.isDesktopSupported()) {
                        java.awt.Desktop desktop = java.awt.Desktop.getDesktop();

                        if (archivoMusica.exists()) {

                            desktop.open(archivoMusica);
                        }
                    } else {
                        System.out.println(
                                "--- Tu sistema no soporta reproducción automática, abre el archivo manualment ---");
                    }
                } catch (Exception e) {
                    System.out.println("--- Error al intentar abrir el reproductor: " + e.getMessage());
                }
                // ------------------------------------------

                return true;
            } else {
                return false;
            }

        } catch (IOException e) {
            return false;
        }
    }
}