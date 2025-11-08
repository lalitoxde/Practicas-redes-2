package Practica3;

import java.net.*;
import java.util.*;

public class ChatClient {
    private static final int PORT = 12345;
    private static final String HOST = "localhost";
    private static final Map<String, String> STICKERS = new HashMap<>();

    static {
        // Stickers con emojis Unicode
        STICKERS.put(":gato:", "🐱");
        STICKERS.put(":perro:", "🐶");
        STICKERS.put(":corazon:", "❤️");
        STICKERS.put(":carafeliz:", "😊");

        // Stickers con arte ASCII
        STICKERS.put(":cohete:",
                "   /\\\n" +
                        "  /  \\\n" +
                        " /____\\\n" +
                        "|      |\n" +
                        "|  🚀  |");
    }

    public static void main(String[] args) {
        try {
            DatagramSocket socket = new DatagramSocket();
            Scanner scanner = new Scanner(System.in);

            System.out.println("Chat iniciado. Comandos disponibles:");
            System.out.println("/stickers - Ver lista de stickers");
            System.out.println("/quit - Salir");

            // Hilo para recibir mensajes
            new Thread(() -> {
                try {
                    byte[] buffer = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                    while (true) {
                        socket.receive(packet);
                        String mensaje = new String(packet.getData(), 0, packet.getLength());
                        System.out.println("\n" + procesarStickers(mensaje) + "\n> ");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            // Envío de mensajes
            while (true) {
                System.out.print("> ");
                String input = scanner.nextLine();

                if (input.equals("/quit")) {
                    break;
                } else if (input.equals("/stickers")) {
                    mostrarStickers();
                    continue;
                }

                byte[] data = input.getBytes();
                DatagramPacket packet = new DatagramPacket(
                        data, data.length, InetAddress.getByName(HOST), PORT);
                socket.send(packet);
            }

            socket.close();
            scanner.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String procesarStickers(String mensaje) {
        for (Map.Entry<String, String> entry : STICKERS.entrySet()) {
            mensaje = mensaje.replace(entry.getKey(), entry.getValue());
        }
        return mensaje;
    }

    private static void mostrarStickers() {
        System.out.println("\n--- STICKERS DISPONIBLES ---");
        for (String key : STICKERS.keySet()) {
            System.out.println(key + " → " + STICKERS.get(key));
        }
        System.out.println("----------------------------\n");
    }
}