package Practica3;

import java.net.*;
import java.util.HashSet;
import java.util.Set;

public class Servidor {
    private static final int PORT = 12345;
    private static Set<InetSocketAddress> clients = new HashSet<>();

    public static void main(String[] args) {
        try {
            DatagramSocket socket = new DatagramSocket(PORT);
            System.out.println("Servidor de chat iniciado en puerto " + PORT);
            System.out.println("Esperando conexiones...");

            while (true) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Agregar cliente si no existe
                InetSocketAddress clientAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());
                clients.add(clientAddress);

                String mensaje = new String(packet.getData(), 0, packet.getLength());
                System.out.println("> " + mensaje);

                // Reenviar a todos los clientes (incluyendo al remitente)
                broadcast(mensaje, socket, clientAddress);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void broadcast(String mensaje, DatagramSocket socket,
            InetSocketAddress sender) {
        byte[] data = mensaje.getBytes();

        for (InetSocketAddress client : clients) {
            try {
                DatagramPacket packet = new DatagramPacket(
                        data, data.length, client.getAddress(), client.getPort());
                socket.send(packet);
            } catch (Exception e) {
                System.out.println("Error enviando a cliente: " + e.getMessage());
                clients.remove(client);
            }
        }
    }
}