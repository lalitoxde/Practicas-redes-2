package Practica3;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatRoom {
    private String name;
    private Map<String, InetSocketAddress> users; // Username -> (Address, Port)

    public ChatRoom(String name) {
        this.name = name;
        this.users = new ConcurrentHashMap<>();
    }

    public void addUsuario(String username, InetAddress address, int port) {
        users.put(username, new InetSocketAddress(address, port));
        System.out.println(username + " se unió a la sala: " + name);
    }

    public void salidaUsuario(String username) {
        users.remove(username);
        System.out.println(username + " dejó la sala: " + name);
    }

    public String getListaUsuarios() {
        if (users.isEmpty()) {
            return "No hay usuarios en la sala";
        }
        StringBuilder lista = new StringBuilder("Usuarios en la sala ->" + name + "<- (" + users.size() + "): ");
        for (String usuario : users.keySet()) {
            lista.append(usuario).append(", ");
        }

        // Quitar la última coma y espacio
        if (lista.length() > 2) {
            lista.setLength(lista.length() - 2);
        }

        return lista.toString();
    }

    // Método para obtener solo los nombres de usuarios (para notificaciones)
    public List<String> getNombresUsuarios() {
        return new ArrayList<>(users.keySet());
    }

    public void broadcast(String message, InetAddress excludeAddress, int excludePort, DatagramSocket socket) {
        for (Map.Entry<String, InetSocketAddress> entry : users.entrySet()) {
            InetSocketAddress userAddress = entry.getValue();

            // No enviar al remitente original
            if (excludeAddress != null &&
                    userAddress.getAddress().equals(excludeAddress) &&
                    userAddress.getPort() == excludePort) {
                continue;
            }

            try {
                byte[] data = message.getBytes();
                DatagramPacket packet = new DatagramPacket(data, data.length,
                        userAddress.getAddress(), userAddress.getPort());
                socket.send(packet);
            } catch (IOException e) {
                System.err.println("Error enviando mensaje a " + entry.getKey() + ": " + e.getMessage());
            }
        }
    }

    public void msgPrivado(String message, String targetUsername, DatagramSocket socket) {
        InetSocketAddress targetAddress = users.get(targetUsername);
        if (targetAddress != null) {
            try {
                byte[] data = message.getBytes();
                DatagramPacket packet = new DatagramPacket(data, data.length,
                        targetAddress.getAddress(), targetAddress.getPort());
                socket.send(packet);
                System.out.println("Mensaje privado enviado a " + targetUsername + ": ");
            } catch (IOException e) {
                System.err.println("Error enviando mensaje privado a " + targetUsername + ": " + e.getMessage());
            }
        } else {
            System.err.println("Usuario " + targetUsername + " no encontrado en la sala");
        }
    }

    // Método para listar usuarios en la sala
    public String getUserList() {
        StringBuilder userList = new StringBuilder();
        for (String username : users.keySet()) {
            userList.append(username).append(",");
        }
        return userList.toString();
    }

    public boolean userExists(String username) {
        return users.containsKey(username);
    }

    public int getUserCount() {
        return users.size();
    }

    public String getName() {
        return name;
    }

    // Método adicional para verificar si un usuario está en la sala
    public boolean containsUser(String username) {
        return users.containsKey(username);
    }

    // Método para obtener la dirección de un usuario específico
    public InetSocketAddress getUserAddress(String username) {
        return users.get(username);
    }
}