package Practica3;

import java.io.IOException;
import java.net.*;
import java.util.*;
/* 
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
                        System.out.println(mensaje);
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
}*/

public class Cliente {
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;
    private String usuario;
    private String sala;
    private boolean running;

    public Cliente(String serverHost, int serverPort, String usuario) throws IOException {
        this.socket = new DatagramSocket();
        this.serverAddress = InetAddress.getByName(serverHost);
        this.serverPort = serverPort;
        this.usuario = usuario;
        this.sala = "general";
        this.running = true;

        joinSala("general");
    }

    public void joinSala(String roomName) {
        sendMessage("JOIN:" + usuario + ":" + roomName + ":");
        this.sala = roomName;
    }

    public void crearSala(String roomName) {
        sendMessage("CREATE:" + usuario + ":" + roomName + ":");
    }

    public void sendChatMessage(String content) {
        sendMessage("MSG:" + usuario + ":" + sala + ":" + content);
    }

    public void listRooms() {
        sendMessage("LIST:" + usuario + "::");
    }

    public void leaveRoom() {
        sendMessage("LEAVE:" + usuario + ":" + sala + ":");
        this.sala = "general";
    }

    private void sendMessage(String message) {
        try {
            byte[] data = message.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, serverPort);
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("Error enviando mensaje: " + e.getMessage());
        }
    }

    public void startReceiver() {
        Thread receiverThread = new Thread(() -> {
            while (running) {
                try {
                    byte[] buffer = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength());
                    displayMessage(message);

                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error recibiendo mensaje: " + e.getMessage());
                    }
                }
            }
        });
        receiverThread.start();
    }

    private void displayMessage(String message) {
        String[] parts = message.split(":", 4);
        String type = parts[0]; // tipo (MSG, ERROR, SUCCESS, SYSTEM, LIST)
        String sender = parts[1];
        String room = parts[2];
        String content = parts.length > 3 ? parts[3] : "";

        switch (type) {
            case "ERROR":
                System.out.println("Error: " + content);
                break;
            case "SUCCESS":
                System.out.println("* " + content + " *");
                break;
            case "SYSTEM":
                System.out.println("* " + content + " *");
                break;
            case "LIST":
                System.out.println("Salas disponibles:");
                String[] rooms = content.split(",");
                for (String roomInfo : rooms) {
                    if (!roomInfo.isEmpty()) {
                        System.out.println("   - " + roomInfo);
                    }
                }
                break;
            case "MSG":
                System.out.println(sender + ": " + content);
                break;
            default:
                System.out.println("Mensaje desconocido: " + message);
        }
    }

    public void stop() {
        running = false;
        socket.close();
    }

    public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Ingresa tu nombre de usuario: ");
            String usuario = scanner.nextLine();

            Cliente comCliente = new Cliente("localhost", 12345, usuario);
            comCliente.startReceiver();

            System.out.println("=== Chat UDP ===");
            System.out.println("Comandos disponibles:");
            System.out.println("/join <sala> - Unirse a una sala");
            System.out.println("/create <sala> - Crear una nueva sala");
            System.out.println("/list - Listar salas disponibles");
            System.out.println("/leave - Salir de la sala actual");
            System.out.println("/exit - Salir del chat");
            System.out.println("Escribe tu mensaje...");

            while (true) {
                String input = scanner.nextLine();

                if ("/exit".equalsIgnoreCase(input)) {
                    break;
                } else if (input.startsWith("/join ")) {
                    String room = input.substring(6);
                    comCliente.joinSala(room);
                } else if (input.startsWith("/create ")) {
                    String room = input.substring(8);
                    comCliente.crearSala(room);
                } else if ("/list".equalsIgnoreCase(input)) {
                    comCliente.listRooms();
                } else if ("/leave".equalsIgnoreCase(input)) {
                    comCliente.leaveRoom();
                } else {
                    comCliente.sendChatMessage(input);
                }
            }

            comCliente.stop();
            scanner.close();
            System.out.println("Chat finalizado.");

        } catch (IOException e) {
            System.err.println("Error iniciando cliente: " + e.getMessage());
        }
    }
}