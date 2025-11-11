package Practica3;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatRoom {
    private String name;
    private Map<String, InetSocketAddress> users;

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
        if (lista.length() > 2) {
            lista.setLength(lista.length() - 2);
        }
        return lista.toString();
    }

    public List<String> getNombresUsuarios() {
        return new ArrayList<>(users.keySet());
    }

    public void broadcast(String message, InetAddress excludeAddress, int excludePort, DatagramSocket socket) {
        for (Map.Entry<String, InetSocketAddress> entry : users.entrySet()) {
            InetSocketAddress userAddress = entry.getValue();
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

    public void broadcastAudio(byte[] audioData, InetAddress excludeAddress, int excludePort, DatagramSocket socket) {
        for (Map.Entry<String, InetSocketAddress> entry : users.entrySet()) {
            InetSocketAddress userAddress = entry.getValue();
            if (excludeAddress != null &&
                    userAddress.getAddress().equals(excludeAddress) &&
                    userAddress.getPort() == excludePort) {
                continue;
            }
            try {
                DatagramPacket packet = new DatagramPacket(audioData, audioData.length,
                        userAddress.getAddress(), userAddress.getPort());
                socket.send(packet);
            } catch (IOException e) {
                System.err.println("Error enviando audio a " + entry.getKey() + ": " + e.getMessage());
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
            } catch (IOException e) {
                System.err.println("Error enviando mensaje privado a " + targetUsername + ": " + e.getMessage());
            }
        }
    }

    public void msgPrivadoAudio(byte[] audioPacket, String targetUsername, DatagramSocket socket) {
        InetSocketAddress targetAddress = users.get(targetUsername);
        if (targetAddress != null) {
            try {
                DatagramPacket packet = new DatagramPacket(audioPacket, audioPacket.length,
                        targetAddress.getAddress(), targetAddress.getPort());
                socket.send(packet);
            } catch (IOException e) {
                System.err.println("Error enviando audio privado a " + targetUsername + ": " + e.getMessage());
            }
        }
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

    public boolean containsUser(String username) {
        return users.containsKey(username);
    }

    public InetSocketAddress getUserAddress(String username) {
        return users.get(username);
    }
}