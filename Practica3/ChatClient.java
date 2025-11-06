package Practica3;

import java.io.*;
import java.net.*;
import java.util.*;

public class ChatClient {
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;
    private String username;
    private String currentRoom;
    private boolean running;

    public ChatClient(String serverHost, int serverPort, String username) throws IOException {
        this.socket = new DatagramSocket();
        this.serverAddress = InetAddress.getByName(serverHost);
        this.serverPort = serverPort;
        this.username = username;
        this.currentRoom = "general";
        this.running = true;

        // Unirse a la sala general automáticamente
        joinRoom("general");
    }

    public void joinRoom(String roomName) {
        sendMessage("JOIN:" + username + ":" + roomName + ":");
        this.currentRoom = roomName;
    }

    public void createRoom(String roomName) {
        sendMessage("CREATE:" + username + ":" + roomName + ":");
    }

    public void sendChatMessage(String content) {
        sendMessage("MSG:" + username + ":" + currentRoom + ":" + content);
    }

    public void listRooms() {
        sendMessage("LIST:" + username + "::");
    }

    public void leaveRoom() {
        sendMessage("LEAVE:" + username + ":" + currentRoom + ":");
        this.currentRoom = "general";
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
            String username = scanner.nextLine();

            ChatClient client = new ChatClient("localhost", 8888, username);
            client.startReceiver();

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
                    client.joinRoom(room);
                } else if (input.startsWith("/create ")) {
                    String room = input.substring(8);
                    client.createRoom(room);
                } else if ("/list".equalsIgnoreCase(input)) {
                    client.listRooms();
                } else if ("/leave".equalsIgnoreCase(input)) {
                    client.leaveRoom();
                } else {
                    client.sendChatMessage(input);
                }
            }

            client.stop();
            scanner.close();
            System.out.println("Chat finalizado.");

        } catch (IOException e) {
            System.err.println("Error iniciando cliente: " + e.getMessage());
        }
    }
}