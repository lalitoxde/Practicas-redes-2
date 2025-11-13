package Practica3;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sound.sampled.AudioPermission;

public class Servidor {
    private static final int PORT = 12345;
    private DatagramSocket socket;
    private Map<String, ChatRoom> Salas;
    private Map<String, AudioPermission> sesionesAudio; // Para reconstruir audio

    public Servidor() throws SocketException {
        this.socket = new DatagramSocket(PORT);
        this.Salas = new ConcurrentHashMap<>();
        this.sesionesAudio = new ConcurrentHashMap<>();
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
                byte[] buffer = new byte[2048];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                procesarPaquete(packet);

            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    private void procesarPaquete(DatagramPacket packet) {
        byte[] data = packet.getData();
        int length = packet.getLength();

        if (length > 0 && data[0] == 1) { // Paquete de audio grabado
            procesarAudioGrabado(packet);
        } else {
            peticionMensaje(packet);
        }
    }

    private void procesarAudioGrabado(DatagramPacket packet) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
            DataInputStream dis = new DataInputStream(bais);

            byte tipo = dis.readByte();
            String usuario = dis.readUTF();
            String sala = dis.readUTF();
            boolean esPrivado = dis.readBoolean();
            String targetUser = dis.readUTF();
            int seqNum = dis.readInt();
            int totalPaquetes = dis.readInt();
            int dataLength = dis.readInt();

            // Verificar integridad
            if (usuario == null || sala == null) {
                System.err.println("Cabecera de audio corrupta");
                return;
            }

            // Reconstruir paquete
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeByte(tipo);
            dos.writeUTF(usuario);
            dos.writeUTF(sala);
            dos.writeBoolean(esPrivado);
            dos.writeUTF(targetUser);
            dos.writeInt(seqNum);
            dos.writeInt(totalPaquetes);
            dos.writeInt(dataLength);

            if (dataLength > 0) {
                byte[] audioData = new byte[dataLength];
                dis.readFully(audioData);
                dos.write(audioData, 0, dataLength);
            }

            byte[] fullPacket = baos.toByteArray();

            // Enrutamiento
            if (esPrivado) {
                enviarAudioPrivado(fullPacket, usuario, targetUser, sala);
            } else {
                ChatRoom room = Salas.get(sala);
                if (room != null) {
                    room.broadcastAudio(fullPacket, packet.getAddress(), packet.getPort(), socket);
                    System.out.println("Audio grabado de " + usuario + " en " + sala + " (paquete " + seqNum + "/"
                            + totalPaquetes + ")");
                } else {
                    System.err.println("Sala " + sala + " no encontrada");
                }
            }

            // Enviar ACK (simulado)
            enviarACK(usuario, seqNum, packet.getAddress(), packet.getPort());

        } catch (IOException e) {
            System.err.println("Error procesando audio grabado: " + e.getMessage());
        }
    }

    private void enviarACK(String usuario, int seqNum, InetAddress address, int port) {
        try {
            String ack = "ACK:" + usuario + "::" + seqNum;
            byte[] data = ack.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("Error enviando ACK: " + e.getMessage());
        }
    }

    private void enviarAudioPrivado(byte[] audioPacket, String sender, String targetUser, String sala) {
        ChatRoom room = Salas.get(sala);
        if (room != null && room.userExists(targetUser)) {
            room.msgPrivadoAudio(audioPacket, targetUser, socket);
            System.out.println("Audio privado de " + sender + " para " + targetUser + " en " + sala);
        } else {
            System.err.println("Usuario " + targetUser + " no encontrado para audio privado");
        }
    }

    private void peticionMensaje(DatagramPacket packet) {
        String mensaje = new String(packet.getData(), 0, packet.getLength());
        String[] estructuraMsg = mensaje.split(":", 4);

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
            for (ChatRoom sala : Salas.values()) {
                if (sala.containsUser(usuario)) {
                    sala.salidaUsuario(usuario);
                    notificacionUsuarios(sala.getName(), sala.getListaUsuarios());
                }
            }

            salaDestino.addUsuario(usuario, address, port);
            sendSuccess(address, port, "Te uniste a la sala: " + nomSala);
            notificacionUsuarios(nomSala, usuario + " se unió a la sala. ");
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

        String formatoMsgPriv = "PRIVATE:" + senderUsername + ":" + nomSala + ":" + privateMessage;
        room.msgPrivado(formatoMsgPriv, targetUsername, socket);
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
            // Si el usuario está en el Lobby Principal, salir completamente del servidor
            if ("Lobby_Principal".equals(nomSala)) {
                // Remover al usuario de todas las salas
                synchronized (Salas) {
                    for (ChatRoom sala : Salas.values()) {
                        if (sala.containsUser(usuario)) {
                            sala.salidaUsuario(usuario);
                            if (!sala.getName().equals("Lobby_Principal")) {
                                notificacionUsuarios(sala.getName(), usuario + " dejó la sala");
                                notificacionUsuarios(sala.getName(), sala.getListaUsuarios());
                            }
                        }
                    }
                }

                // Enviar comando especial para que el cliente se cierre
                String exitCommand = "EXIT:" + usuario
                        + ":Lobby_Principal:Has salido del servidor. Cerrando aplicación...";
                mensajeServidor(address, port, exitCommand);
                // Notificar al Lobby Principal que el usuario salió del servidor
                notificacionUsuarios("Lobby_Principal", usuario + " ha salido del CHAT.");
                notificacionUsuarios("Lobby_Principal", room.getListaUsuarios());

            } else {
                // Si está en otra sala, moverlo al Lobby Principal
                room.salidaUsuario(usuario);

                ChatRoom salaGeneral = Salas.get("Lobby_Principal");
                if (salaGeneral != null) {
                    salaGeneral.addUsuario(usuario, address, port);
                    sendSuccess(address, port, "Saliste de " + nomSala + " y fuiste redirigido al Lobby Principal");
                    notificacionUsuarios("Lobby_Principal", usuario + " se unió a la sala");
                    notificacionUsuarios("Lobby_Principal", salaGeneral.getListaUsuarios());
                }

                notificacionUsuarios(nomSala, usuario + " dejó la sala " + nomSala);
                notificacionUsuarios(nomSala, room.getListaUsuarios());
            }
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