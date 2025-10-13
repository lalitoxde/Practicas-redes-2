
/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Practica2;

/**
 *
 * @author Lenovo
 */
// Importa la clase Player de la biblioteca externa JLayer, necesaria para reproducir audio MP3.
import javazoom.jl.player.Player;
// Importa las clases para manejar flujos de datos (Input/Output) y la serialización de objetos.
import java.io.*;
// Importa las clases para la comunicación por red, como Sockets y Paquetes de Datagramas.
import java.net.*;

/**
 * Representa el programa Cliente que se conecta al ServidorMP3.
 * Su función es recibir los paquetes de audio de una canción vía UDP,
 * gestionar la pérdida de paquetes usando un temporizador (timeout) y el
 * algoritmo Go-Back-N, y reproducir la canción en tiempo real (streaming)
 * a medida que llegan los datos.
 */
public class ClienteMP3 {
    // --- PARÁMETROS DE CONFIGURACIÓN ---

    /** El puerto en el que el servidor está escuchando. Debe coincidir con el del servidor. */
    private static final int PUERTO_SERVIDOR = 9876;
    /** La dirección del servidor. "localhost" significa que está en la misma máquina. */
    private static final String HOST_SERVIDOR = "localhost";
    /**
     * El tiempo máximo en milisegundos que el cliente esperará por un paquete.
     * Si este tiempo se excede, se asume que el paquete se perdió.
     */
    private static final int TIMEOUT = 2000; // 2 segundos de timeout

    /**
     * Método principal que inicia el cliente.
     * @param args Argumentos de la línea de comandos (no se usan).
     * @throws IOException Si ocurre un error de red.
     */
    public static void main(String[] args) throws IOException {
        // Crea un socket UDP para la comunicación del cliente.
        DatagramSocket socketCliente = new DatagramSocket();
        // Obtiene la dirección IP del servidor a partir de su nombre de host.
        InetAddress direccionServidor = InetAddress.getByName(HOST_SERVIDOR);

        // --- INICIO DE LA COMUNICACIÓN ---
        // Envía una señal de "INICIAR" al servidor para indicarle que está listo para recibir la canción.
        byte[] datosInicio = "INICIAR".getBytes();
        DatagramPacket paqueteInicio = new DatagramPacket(datosInicio, datosInicio.length, direccionServidor, PUERTO_SERVIDOR);
        socketCliente.send(paqueteInicio);
        System.out.println("Conectado al servidor. Esperando datos de la canción...");

        // --- VARIABLES DE ESTADO ---
        long proximoPaqueteEsperado = 0; // Contador para saber qué número de paquete se espera recibir.
        boolean transmisionTerminada = false; // Bandera para controlar el fin del bucle principal.

        // --- CONFIGURACIÓN DEL REPRODUCTOR DE AUDIO (STREAMING) ---
        PipedOutputStream pipeOut = null; // El "escritor" de la tubería de audio.
        try {
            // Se crea una "tubería" en memoria: un flujo de entrada conectado a un flujo de salida.
            PipedInputStream pis = new PipedInputStream();
            pipeOut = new PipedOutputStream(pis);

            // Se crea y se inicia un nuevo hilo (Thread) dedicado exclusivamente a la reproducción.
            // Esto es crucial para que la recepción de paquetes y la reproducción de audio
            // puedan ocurrir simultáneamente sin bloquearse mutuamente.
            new Thread(() -> {
                try {
                    // La clase Player de JLayer se "engancha" al extremo de lectura de la tubería.
                    // Se quedará esperando datos y los reproducirá tan pronto como lleguen.
                    new Player(pis).play();
                } catch (Exception e) {
                    System.err.println("Error en el hilo de reproducción: " + e.getMessage());
                }
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // --- BUCLE PRINCIPAL DE RECEPCIÓN DE PAQUETES ---
        while (!transmisionTerminada) {
            byte[] bufferRecepcion = new byte[8192]; // Un buffer grande para recibir el paquete serializado.
            DatagramPacket paqueteRecibido = new DatagramPacket(bufferRecepcion, bufferRecepcion.length);

            try {
                // Establece el temporizador. La siguiente línea se bloqueará MÁXIMO por el tiempo definido en TIMEOUT.
                socketCliente.setSoTimeout(TIMEOUT);
                // Intenta recibir un paquete. Si no llega nada antes del timeout, lanzará una SocketTimeoutException.
                socketCliente.receive(paqueteRecibido);

                // --- PROCESAMIENTO DEL PAQUETE RECIBIDO ---
                // Convierte los bytes recibidos de vuelta a un objeto AudioPacket (Deserialización).
                ByteArrayInputStream byteStream = new ByteArrayInputStream(paqueteRecibido.getData());
                ObjectInputStream objectStream = new ObjectInputStream(byteStream);
                AudioPacket paqueteAudio = (AudioPacket) objectStream.readObject();

                System.out.println("Recibido paquete: " + paqueteAudio.getSequenceNumber());

                // --- LÓGICA DE CONTROL DE ERROR (GO-BACK-N) ---
                if (paqueteAudio.getSequenceNumber() == proximoPaqueteEsperado) {
                    // CASO 1: El paquete es el correcto y esperado.
                    
                    // Se extraen los datos de audio del paquete.
                    byte[] datosAudio = paqueteAudio.getData();
                    // Se escriben los datos en la tubería, donde el hilo reproductor los tomará y los convertirá en sonido.
                    pipeOut.write(datosAudio);

                    // Se envía una confirmación (ACK) al servidor por el paquete recibido correctamente.
                    enviarACK(proximoPaqueteEsperado, socketCliente, direccionServidor);
                    // Se incrementa el contador para esperar el siguiente paquete en la secuencia.
                    proximoPaqueteEsperado++;

                    // Si este paquete tiene la bandera de ser el último, terminamos la transmisión.
                    if (paqueteAudio.isLastPacket()) {
                        transmisionTerminada = true;
                        System.out.println("¡Último paquete recibido! Terminando.");
                    }
                } else {
                    // CASO 2: El paquete está desordenado (ej. se esperaba el 5 y llegó el 7).
                    System.err.println("Paquete desordenado. Esperaba " + proximoPaqueteEsperado + " pero recibí " + paqueteAudio.getSequenceNumber() + ". Descartando.");
                    // Se descarta el paquete y se reenvía el ACK del último paquete bueno recibido.
                    // Esto le indica al servidor que "retroceda" y reenvíe desde el paquete que falta.
                    enviarACK(proximoPaqueteEsperado - 1, socketCliente, direccionServidor);
                }

            } catch (SocketTimeoutException e) {
                // CASO 3: El paquete esperado se perdió (el temporizador expiró).
                System.err.println("Timeout! No se recibió el paquete " + proximoPaqueteEsperado + ". Pidiendo retransmisión.");
                // Se reenvía el ACK del último paquete bueno para activar el Go-Back-N en el servidor.
                enviarACK(proximoPaqueteEsperado - 1, socketCliente, direccionServidor);
            } catch (ClassNotFoundException | IOException e) {
                // Captura otros posibles errores de red o deserialización.
                e.printStackTrace();
            }
        }
        
        // --- LIMPIEZA FINAL ---
        pipeOut.close(); // Cierra la tubería de audio.
        socketCliente.close(); // Cierra la conexión de red.
        System.out.println("Cliente desconectado.");
    }

    /**
     * Método "ayudante" para enviar un mensaje de confirmación (ACK) al servidor.
     * @param ackNum El número de secuencia del paquete que se está confirmando.
     * @param socket El socket del cliente para enviar el ACK.
     * @param direccion La dirección del servidor.
     * @throws IOException Si ocurre un error al enviar el paquete.
     */
    private static void enviarACK(long ackNum, DatagramSocket socket, InetAddress direccion) throws IOException {
        if (ackNum < 0) return; // No enviar ACKs para paquetes negativos (al inicio).
        String ackMsg = "ACK:" + ackNum;
        byte[] datosACK = ackMsg.getBytes();
        DatagramPacket paqueteACK = new DatagramPacket(datosACK, datosACK.length, direccion, PUERTO_SERVIDOR);
        socket.send(paqueteACK);
        System.out.println("Enviado ACK para paquete: " + ackNum);
    }
}
