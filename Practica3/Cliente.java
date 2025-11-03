package Practica3;

import java.net.*;
import java.util.*;

public class Cliente {
    private static final int PORT = 12345;
    private static final String HOST = "localhost";
    private static final Map<String, String> STICKERS = new HashMap<>();

    static {
        STICKERS.put(":gato:", "\n" +
                " /\\_/\\\n" +
                "( o.o )\n" +
                " > ^ <"); // gato

        STICKERS.put(":perro:", "\n" +
                "  /\\_/\n" +
                " (    @\\___\n" +
                " /         O\n" +
                "/   (_____/\n" +
                "/_____/   U"); // perro

        STICKERS.put(":corazon:", "\n" +
                " ** **\n" +
                "*******\n" +
                " *****\n" +
                "  ***\n" +
                "   *\n"); // corazon

        STICKERS.put(":carafeliz:", "\n" +
                "  -----\n" +
                " | O O |\n" +
                " |  U  |\n" +
                "  -----"); // carafeliz
    }

    public static void main(String[] args) {
        try {
            DatagramSocket socket = new DatagramSocket();
            Scanner scanner = new Scanner(System.in);
            String usuario = "Usuario" + (int) (Math.random() * 1000);

            System.out.println("Chat iniciado como: " + usuario + "\n");
            System.out.println("Comandos:");
            System.out.println(">/stickers para ver stickers disponibles");
            System.out.println(">/quit para ver salir del chat");
            System.out.println(">/usuario para cambiar nombre de usuario");

            // Hilo para recibir mensajes
            new Thread(() -> {
                try {
                    byte[] buffer = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                    while (true) {
                        socket.receive(packet);
                        String mensaje = new String(packet.getData(), 0, packet.getLength());
                        System.out.println("\n" + mensaje + "\n> ");
                    }
                } catch (Exception e) {
                    System.out.println("Conexión cerrada");
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
                } else if (input.equals("/usuario")) {
                    System.out.print("Nuevo nombre de usuario: ");
                    usuario = scanner.nextLine();
                    continue;
                }

                // Formato: "Usuario: mensaje"
                String mensajeCompleto = usuario + ": " + procesarStickers(input);
                byte[] data = mensajeCompleto.getBytes();

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
        String resultado = mensaje;
        for (Map.Entry<String, String> entry : STICKERS.entrySet()) {
            resultado = resultado.replace(entry.getKey(), entry.getValue());
        }
        return resultado;
    }

    private static void mostrarStickers() {
        System.out.println("\n--- STICKERS DISPONIBLES ---");
        for (String key : STICKERS.keySet()) {
            System.out.println(key + " → " + STICKERS.get(key));
        }
        System.out.println("----------------------------\n");
    }
}