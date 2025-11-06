package Practica3;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 8888;
    private DatagramSocket socket;
    private Map<String, ChatRoom> rooms;

    public ChatServer() throws SocketException {
        this.socket = new DatagramSocket(PORT);
        this.rooms = new ConcurrentHashMap<>();

        // Crear sala general por defecto
        rooms.put("general", new ChatRoom("general"));

        System.out.println("Servidor de chat iniciado en puerto: " + PORT);
    }

    public void start() {
        while (true) {
            try {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Procesar mensaje
                processMessage(packet);

            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    private void processMessage(DatagramPacket packet) {
        String message = new String(packet.getData(), 0, packet.getLength());
        String[] parts = message.split(":", 4); // formato: COMANDO:USERNAME:ROOM:DATA

        if (parts.length < 3) {
            sendError(packet.getAddress(), packet.getPort(), "Mensaje inválido");
            return;
        }

        String command = parts[0];
        String username = parts[1];
        String roomName = parts[2];
        String data = parts.length > 3 ? parts[3] : "";

        switch (command) {
            case "JOIN":
                joinRoom(username, roomName, packet.getAddress(), packet.getPort());
                break;
            case "CREATE":
                createRoom(username, roomName, packet.getAddress(), packet.getPort());
                break;
            case "MSG":
                broadcastMessage(username, roomName, data, packet.getAddress(), packet.getPort());
                break;
            case "LIST":
                listRooms(packet.getAddress(), packet.getPort());
                break;
            case "LEAVE":
                leaveRoom(username, roomName, packet.getAddress(), packet.getPort());
                break;
            default:
                sendError(packet.getAddress(), packet.getPort(), "Comando no válido: " + command);
        }
    }

    private void joinRoom(String username, String roomName, InetAddress address, int port) {
        synchronized (rooms) {
            if (!rooms.containsKey(roomName)) {
                sendError(address, port, "La sala '" + roomName + "' no existe");
                return;
            }

            // Remover usuario de otras salas
            for (ChatRoom room : rooms.values()) {
                if (room.containsUser(username)) {
                    room.salidaUsuario(username);
                }
            }

            // Agregar usuario a la nueva sala
            rooms.get(roomName).addUsuario(username, address, port);
            sendSuccess(address, port, "Te uniste a la sala: " + roomName);

            // Notificar a otros usuarios
            broadcastSystemMessage(roomName, username + " se unió a la sala");
        }
    }

    private void createRoom(String username, String roomName, InetAddress address, int port) {
        synchronized (rooms) {
            if (rooms.containsKey(roomName)) {
                sendError(address, port, "La sala '" + roomName + "' ya existe");
                return;
            }

            rooms.put(roomName, new ChatRoom(roomName));
            sendSuccess(address, port, "Sala creada: " + roomName);

            // Unir automáticamente al usuario a la nueva sala
            // joinRoom(username, roomName, address, port);
        }
    }

    private void broadcastMessage(String username, String roomName, String message,
            InetAddress senderAddress, int senderPort) {
        ChatRoom room = rooms.get(roomName);

        if (room != null) {
            String formattedMessage = "MSG:[" + username + "]: ->" + roomName + "<- :" + message;
            room.broadcast(formattedMessage, senderAddress, senderPort, socket);
        } else {
            sendError(senderAddress, senderPort, "No estás en ninguna sala");
        }
    }

    private void listRooms(InetAddress address, int port) {
        StringBuilder roomList = new StringBuilder("");
        for (String roomName : rooms.keySet()) {
            ChatRoom room = rooms.get(roomName);
            roomList.append(roomName).append("( Usuarios conectados: ").append(room.getUserCount()).append(" ),");
        }
        String message = "LIST:" + "server" + ":" + ":" + roomList.toString();
        sendMessage(address, port, message);
    }

    private void leaveRoom(String username, String roomName, InetAddress address, int port) {
        ChatRoom room = rooms.get(roomName);
        if (room != null) {
            room.salidaUsuario(username);
            broadcastSystemMessage(roomName, username + " dejó la sala");
            sendSuccess(address, port, "Saliste de la sala: " + roomName);
        }
    }

    private void broadcastSystemMessage(String roomName, String content) {
        String systemMsg = "SYSTEM::" + roomName + ":" + content;
        ChatRoom room = rooms.get(roomName);
        if (room != null) {
            room.broadcast(systemMsg, null, -1, socket);
        }
    }

    private void sendMessage(InetAddress address, int port, String content) {
        try {
            byte[] data = content.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("Error enviando mensaje: " + e.getMessage());
        }
    }

    private void sendSuccess(InetAddress address, int port, String message) {
        sendMessage(address, port, "SUCCESS:::" + message);
    }

    private void sendError(InetAddress address, int port, String error) {
        sendMessage(address, port, "ERROR:::" + error);
    }

    public static void main(String[] args) {
        try {
            ChatServer server = new ChatServer();
            server.start();
        } catch (SocketException e) {
            System.err.println("No se pudo iniciar el servidor: " + e.getMessage());
        }
    }
}