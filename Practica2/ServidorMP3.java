package Practica2;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Lenovo
 */
import java.io.*;
import java.net.*; // Esta es tu caja de herramientas para todo lo relacionado con redes (Network).
import java.nio.file.Files;//Esta es otra clase "ayudante" que te da un montón de herramientas para operar sobre archivos.
import java.nio.file.Path;//Representa la ruta o ubicación de un archivo o directorio de una manera moderna y flexible.
import java.nio.file.Paths;//Es una clase "ayudante" o "fábrica" cuyo único propósito es crear objetos Path.
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Representa el programa Servidor para la transmisión de un archivo MP3.
 * Su función es leer un archivo de audio, dividirlo en paquetes numerados,
 * y enviarlos a un cliente a través del protocolo UDP. Implementa los algoritmos
 * de Ventana Deslizante para el control de flujo y Go-Back-N para la recuperación
 * de errores (pérdida de paquetes) mediante un temporizador (timeout).
 */
public class ServidorMP3 {
    // --- PARÁMETROS CONFIGURABLES ---

    /** El puerto UDP en el que el servidor escuchará las conexiones de los clientes. */
    private static final int PUERTO = 9876;
    /** El tamaño en bytes de los datos de audio en cada paquete. 4096 bytes = 4 KB. */
    private static final int TAMANO_PAQUETE = 4096;
    /** El tamaño de la ventana deslizante (K). El número de paquetes que se pueden enviar sin recibir confirmación. */
    private static final int TAMANO_VENTANA = 10;

    /**
     * Método principal que inicia el servidor.
     * @param args Argumentos de la línea de comandos (no se usan).
     * @throws IOException Si ocurre un error al leer el archivo o al abrir el socket.
     */
    public static void main(String[] args) throws IOException {
        System.out.println("Iniciando servidor...");

        // --- 1. PREPARACIÓN DEL ARCHIVO MP3 ---
        // Define la ruta del archivo MP3 que se va a transmitir.
        Path rutaCancion = Paths.get("Instant Crush.mp3");
        // Lee el archivo completo en un único arreglo de bytes.
        byte[] archivoBytes = Files.readAllBytes(rutaCancion);
        // Crea una lista para almacenar los objetos AudioPacket.
        List<AudioPacket> paquetes = new ArrayList<>();
        long seqNum = 0; // Contador para el número de secuencia.
        // Bucle para dividir el arreglo de bytes en trozos más pequeños.
        for (int i = 0; i < archivoBytes.length; i += TAMANO_PAQUETE) {
            // Calcula el final del trozo actual, asegurando que no se pase del final del archivo.
            int fin = Math.min(i + TAMANO_PAQUETE, archivoBytes.length);
            // Copia el fragmento del archivo en un nuevo arreglo de bytes.
            byte[] datosPaquete = Arrays.copyOfRange(archivoBytes, i, fin);
            // Determina si este es el último paquete de la secuencia.
            boolean esUltimo = (fin == archivoBytes.length);
            // Crea un nuevo objeto AudioPacket y lo agrega a la lista.
            paquetes.add(new AudioPacket(seqNum++, datosPaquete, esUltimo));
        }
        System.out.println("Archivo MP3 cargado y dividido en " + paquetes.size() + " paquetes.");

        // Abre un socket UDP en el puerto especificado para la comunicación.
        DatagramSocket socketServidor = new DatagramSocket(PUERTO);
        byte[] bufferRecepcion = new byte[1024]; // Buffer para recibir ACKs del cliente.

        // Bucle infinito para que el servidor pueda atender a múltiples clientes (uno tras otro).
        while (true) {
            System.out.println("\nEsperando conexión de un cliente...");
            // Prepara un paquete para recibir la señal de inicio del cliente.
            DatagramPacket paqueteInicio = new DatagramPacket(bufferRecepcion, bufferRecepcion.length);
            // Esta línea es "bloqueante": el programa se detiene aquí hasta que recibe un paquete.
            socketServidor.receive(paqueteInicio);
            // Guarda la dirección IP y el puerto del cliente para poder enviarle paquetes de vuelta.
            InetAddress direccionCliente = paqueteInicio.getAddress();
            int puertoCliente = paqueteInicio.getPort();
            System.out.println("Cliente conectado. Iniciando transmisión de la canción...");

            // --- 2. LÓGICA DE VENTANA DESLIZANTE Y GO-BACK-N ---
            int baseVentana = 0; // El número de secuencia del paquete más antiguo no confirmado.
            int proximoSeqNum = 0; // El número de secuencia del próximo paquete a enviar.
            int totalPaquetes = paquetes.size();

            // El bucle de transmisión principal. Continúa mientras haya paquetes sin confirmar.
            while (baseVentana < totalPaquetes) {
                // --- FASE DE ENVÍO DE LA VENTANA ---
                // Envía paquetes mientras el "puntero" de envío esté dentro de la ventana actual.
                while (proximoSeqNum < baseVentana + TAMANO_VENTANA && proximoSeqNum < totalPaquetes) {
                    AudioPacket paqueteAEnviar = paquetes.get(proximoSeqNum);

                    // Proceso para convertir el objeto AudioPacket a un arreglo de bytes (Serialización).
                    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                    ObjectOutputStream objectStream = new ObjectOutputStream(byteStream);
                    objectStream.writeObject(paqueteAEnviar);
                    byte[] datosAEnviar = byteStream.toByteArray();

                    // Crea el paquete de datagrama UDP y lo envía al cliente.
                    DatagramPacket paqueteEnvio = new DatagramPacket(datosAEnviar, datosAEnviar.length, direccionCliente, puertoCliente);
                    socketServidor.send(paqueteEnvio);
                    System.out.println("Enviado paquete: " + proximoSeqNum);
                    proximoSeqNum++;
                }

                // --- FASE DE ESPERA DE CONFIRMACIÓN (ACK) ---
                try {
                    // Establece un temporizador. El servidor esperará un máximo de 5 segundos por un ACK.
                    socketServidor.setSoTimeout(5000);
                    // Prepara un paquete para recibir el ACK.
                    DatagramPacket paqueteACK = new DatagramPacket(bufferRecepcion, bufferRecepcion.length);
                    // Se bloquea esperando el ACK. Si no llega en 5 segundos, lanzará una SocketTimeoutException.
                    socketServidor.receive(paqueteACK);

                    // Si se recibe un ACK, se procesa.
                    String ackMsg = new String(paqueteACK.getData(), 0, paqueteACK.getLength());
                    int ackNum = Integer.parseInt(ackMsg.split(":")[1]);
                    System.out.println("ACK recibido para paquete: " + ackNum);

                    // Desliza la ventana: La base se mueve al siguiente paquete después del confirmado.
                    // Esto indica que todos los paquetes hasta 'ackNum' han sido recibidos correctamente.
                    baseVentana = ackNum + 1;

                } catch (SocketTimeoutException e) {
                    // --- MANEJO DE ERROR (GO-BACK-N) ---
                    // Este bloque se ejecuta si el temporizador expira.
                    System.err.println("Timeout! El ACK para el paquete " + baseVentana + " se perdió.");
                    System.err.println("Retrocediendo (Go-Back-N): Reenviando desde el paquete " + baseVentana);
                    // Se resetea el puntero de envío a la base de la ventana actual.
                    // Esto forzará la retransmisión de todos los paquetes de la ventana en la siguiente iteración.
                    proximoSeqNum = baseVentana;
                }
            }
            System.out.println("Transmisión completada para el cliente.");
        }
    }
}





