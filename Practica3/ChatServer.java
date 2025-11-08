package Practica3;

import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatServer {
    private static final int PORT = 12345;
    private static final Map<String, String> STICKERS = new HashMap<>();

    static {
        STICKERS.put(":gato:", "🐱");
        STICKERS.put(":perro:", "🐶");
        STICKERS.put(":corazon:", "❤️");
    }

    private static List<InetSocketAddress> clients = new ArrayList<>();

    public static void main(String[] args) {
        try {
            DatagramSocket socket = new DatagramSocket(PORT);
            System.out.println("Servidor de chat iniciado en puerto " + PORT);

            while (true) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Agregar cliente si no existe
                InetSocketAddress clientAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());
                if (!clients.contains(clientAddress)) {
                    clients.add(clientAddress);
                }

                String mensaje = new String(packet.getData(), 0, packet.getLength());
                String mensajeProcesado = procesarStickers(mensaje);

                // Reenviar a todos los clientes
                broadcast(mensajeProcesado, socket, clientAddress);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void broadcast(String mensaje, DatagramSocket socket,
            InetSocketAddress sender) {
        byte[] data = mensaje.getBytes();

        for (InetSocketAddress client : clients) {
            if (!client.equals(sender)) {
                try {
                    DatagramPacket packet = new DatagramPacket(
                            data, data.length, client.getAddress(), client.getPort());
                    socket.send(packet);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static String procesarStickers(String mensaje) {
        for (Map.Entry<String, String> entry : STICKERS.entrySet()) {
            mensaje = mensaje.replace(entry.getKey(), entry.getValue());
        }
        return mensaje;
    }
}