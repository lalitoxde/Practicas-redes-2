package Practica3;

import java.io.IOException;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Servidor {
    private static final int PORT = 12345;
    private DatagramSocket socket;
    // private static Set<InetSocketAddress> clientes = new HashSet<>();
    private Map<String, ChatRoom> Salas;

    public Servidor() throws SocketException {
        this.socket = new DatagramSocket(PORT);
        this.Salas = new ConcurrentHashMap<>();
        // Crear sala general por defecto
        Salas.put("Lobby_Principal", new ChatRoom("Lobby_Principal"));
    }

    public static void main(String[] args) {
        try {
            // DatagramSocket socket = new DatagramSocket(PORT);
            System.out.println("Servidor de chat iniciado en puerto " + PORT);
            System.out.println("Esperando conexiones...");
            ChatServer server = new ChatServer();
            server.start();

        } catch (Exception e) {
            System.err.println("No se pudo iniciar el servidor: " + e.getMessage());
        }
    }

    public void start() {
        while (true) {
            try {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Verificamos el tipo de petición del usuario
                peticionMensaje(packet);

            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    private void peticionMensaje(DatagramPacket packet) {
        String mensaje = new String(packet.getData(), 0, packet.getLength());
        String[] estructuraMsg = mensaje.split(":", 4); // formato: peticion:Nombre:Sala:mensaje

        if (estructuraMsg.length < 3) {
            sendError(packet.getAddress(), packet.getPort(), "Mensaje inválido");
            return;
        }

        String peticion = estructuraMsg[0];
        String usuario = estructuraMsg[1];
        String nomSala = estructuraMsg[2];
        String msg = estructuraMsg.length > 3 ? estructuraMsg[3] : "";

        switch (peticion) {
            case "entrar":
                joinSala(usuario, nomSala, packet.getAddress(), packet.getPort());
                break;
            case "crearSala":
                crearSala(usuario, nomSala, packet.getAddress(), packet.getPort());
                break;
            case "MSG":
                enviarMensaje(usuario, nomSala, msg, packet.getAddress(), packet.getPort());
                break;
            case "ListarSalas":
                listSalas(packet.getAddress(), packet.getPort());
                break;
            case "Salir":
                salir(usuario, nomSala, packet.getAddress(), packet.getPort());
                break;
            default:
                sendError(packet.getAddress(), packet.getPort(), "Comando no válido: " + peticion);
        }
    }

    private void joinSala(String usuario, String nomSala, InetAddress address, int port) {
        synchronized (Salas) {
            if (!Salas.containsKey(nomSala)) {
                sendError(address, port, "La sala '" + nomSala + "' no existe");
                return;
            }

            // Verificar y remover usuario de otras salas
            for (ChatRoom room : Salas.values()) {
                if (room.containsUser(usuario)) {
                    room.salidaUsuario(usuario);
                }
            }

            // Agregar usuario a la nueva sala
            Salas.get(nomSala).addUsuario(usuario, address, port);
            sendSuccess(address, port, "Te uniste a la sala: " + nomSala);

            // Notificar a otros usuarios
            notificacionUsuarios(nomSala, usuario + " se unió a la sala");
        }
    }

    private void crearSala(String usuario, String nomSala, InetAddress address, int port) {
        synchronized (Salas) {
            if (Salas.containsKey(nomSala)) {
                sendError(address, port, "La sala '" + nomSala + "' ya existe");
                return;
            }

            Salas.put(nomSala, new ChatRoom(nomSala));
            sendSuccess(address, port, "Sala creada: " + nomSala);

            // Unir automáticamente al usuario a la nueva sala
            // joinSala(usuario, nomSala, address, port);
        }
    }

    private void enviarMensaje(String usuario, String nomSala, String message,
            InetAddress senderAddress, int senderPort) {
        ChatRoom room = Salas.get(nomSala);

        if (room != null) {
            String formatoMensaje = "MSG:[" + usuario + "]:" + nomSala + ":" + message;
            room.broadcast(formatoMensaje, senderAddress, senderPort, socket);
        } else {
            sendError(senderAddress, senderPort, "No estás en ninguna sala");
        }
    }

    private void listSalas(InetAddress address, int port) {
        StringBuilder roomList = new StringBuilder("");
        for (String nomSala : Salas.keySet()) {
            ChatRoom room = Salas.get(nomSala);
            roomList.append(nomSala).append("(").append(room.getUserCount()).append("),");
        }
        String msgSalas = "LIST:" + "server" + ":" + ":" + roomList.toString();
        mensajeServidor(address, port, msgSalas);
    }

    private void salir(String usuario, String nomSala, InetAddress address, int port) {
        ChatRoom room = Salas.get(nomSala);
        if (room != null) {
            room.salidaUsuario(usuario);
            notificacionUsuarios(nomSala, usuario + " dejó la sala");
            sendSuccess(address, port, "Saliste de la sala: " + nomSala);
        }
    }

    private void notificacionUsuarios(String nomSala, String content) {
        String systemMsg = "SYSTEM::" + nomSala + ":" + content;
        ChatRoom room = Salas.get(nomSala);
        if (room != null) {
            room.broadcast(systemMsg, null, -1, socket);
        }
    }

    private void mensajeServidor(InetAddress address, int port, String content) {
        try {
            byte[] data = content.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("Error enviando mensaje: " + e.getMessage());
        }
    }

    private void sendSuccess(InetAddress address, int port, String message) {
        mensajeServidor(address, port, "SUCCESS:::" + message);
    }

    private void sendError(InetAddress address, int port, String error) {
        mensajeServidor(address, port, "ERROR:::" + error);
    }
}