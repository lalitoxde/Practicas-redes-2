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
        System.out.println("Servidor de chat iniciado en puerto " + PORT);
    }

    public static void main(String[] args) {
        try {
            System.out.println("Esperando conexiones...");
            Servidor server = new Servidor();
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
            case "msgPrivado":
                sendmsgPrivado(usuario, nomSala, msg, packet.getAddress(), packet.getPort());
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
            ChatRoom salaDestino = Salas.get(nomSala);
            // Verificar y remover usuario de otras salas
            for (ChatRoom sala : Salas.values()) {
                if (sala.containsUser(usuario)) {
                    sala.salidaUsuario(usuario);
                    notificacionUsuarios(sala.getName(), sala.getListaUsuarios());
                }
            }

            // Agregar usuario a la nueva sala
            salaDestino.addUsuario(usuario, address, port);
            sendSuccess(address, port, "Te uniste a la sala: " + nomSala);
            notificacionUsuarios(nomSala, usuario + " se unió a la sala. ");
            // Notificar a otros usuarios
            notificacionUsuarios(nomSala, salaDestino.getListaUsuarios());
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

    private void sendmsgPrivado(String senderUsername, String nomSala, String data,
            InetAddress senderAddress, int senderPort) {
        // Formato de data: "targetUsername:mensaje"
        String[] privateParts = data.split(":", 2);
        if (privateParts.length < 2) {
            sendError(senderAddress, senderPort, "Formato incorrecto. Usa: #priv <usuario> <mensaje>");
            return;
        }

        String targetUsername = privateParts[0];
        String privateMessage = privateParts[1];

        ChatRoom room = Salas.get(nomSala);
        if (room == null) {
            sendError(senderAddress, senderPort, "No estás en ninguna sala");
            return;
        }

        if (!room.userExists(targetUsername)) {
            sendError(senderAddress, senderPort, "Usuario '" + targetUsername + "' no encontrado en la sala");
            return;
        }

        if (senderUsername.equals(targetUsername)) {
            sendError(senderAddress, senderPort, "No puedes enviarte mensajes a ti mismo");
            return;
        }

        // Enviar mensaje privado al destinatario
        String formatoMsgPriv = "PRIVATE:" + senderUsername + ":" + nomSala + ":" + privateMessage;
        room.msgPrivado(formatoMsgPriv, targetUsername, socket);

        // Confirmar al remitente que se envió
        sendSuccess(senderAddress, senderPort, "Mensaje privado enviado a " + targetUsername);
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
            // notificacionUsuarios(nomSala, room.getListaUsuarios());
            ChatRoom salaGeneral = Salas.get("Lobby_Principal");
            if (salaGeneral != null && !salaGeneral.containsUser(usuario)) {
                salaGeneral.addUsuario(usuario, address, port);

                // Enviar mensaje de éxito
                sendSuccess(address, port, "Saliste de " + nomSala + " y fuiste redirigido al Lobby");
                // String listaUsuarios = salaGeneral.getListaUsuarios();
                // mensajeServidor(address, port, "SYSTEM:::" + listaUsuarios);

                // Notificar a los demás en la sala general
                notificacionUsuarios("Lobby_Principal", usuario + " se unió a la sala");
                notificacionUsuarios("Lobby_Principal", salaGeneral.getListaUsuarios());
            }
            notificacionUsuarios(nomSala, usuario + " dejó la sala " + nomSala);
            notificacionUsuarios(nomSala, room.getListaUsuarios());
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