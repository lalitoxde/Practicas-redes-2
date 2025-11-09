package Practica3;

import java.io.IOException;
import java.net.*;
import java.util.*;

public class Cliente {
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;
    private String usuario;
    private String sala;
    private boolean banderaFin;
    private static final Map<String, String> STICKERS = new HashMap<>();

    public Cliente(String serverHost, int serverPort, String usuario) throws IOException {
        this.socket = new DatagramSocket();
        this.serverAddress = InetAddress.getByName(serverHost);
        this.serverPort = serverPort;
        this.usuario = usuario;
        this.sala = "Lobby_Principal";
        this.banderaFin = true;

        joinSala("Lobby_Principal");
    }

    static {
        STICKERS.put(":gato:", "\n" +
                " /\\_/\\\n" +
                "( o.o )\n" +
                " > ^ <\n"); // gato

        STICKERS.put(":perro:", "\n" +
                "  /\\_/\n" +
                " (    @\\___\n" +
                " /         O\n" +
                "/   (_____/\n" +
                "/_____/   U\n"); // perro

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
                "  -----\n"); // carafeliz
    }

    public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Ingresa tu nombre de usuario: ");
            String usuario = scanner.nextLine();

            Cliente comCliente = new Cliente("localhost", 12345, usuario);
            comCliente.startReceiver();

            System.out.println("=== Discord para pobres ===");
            System.out.println("Bienvenido " + usuario + " disfruta del chat!\n");
            System.out.println("Lista de comandos:");
            System.out.println("/entrar <nombreSala> -> Unirse a una sala");
            System.out.println("/crear <nombreSala> -> Crear una nueva sala");
            System.out.println("/listar -> Lista las salas disponibles");
            System.out.println("/stickers -> Lista de los stickers disponibles");
            System.out.println("#priv <usuarioDestino> <mensaje> -> Envia mensajes privados");
            System.out.println("/salir -> Salir de la sala actual");
            System.out.println("/exit -> Salir del chat");
            System.out.println("Envia un mensaje");

            while (true) {
                String input = scanner.nextLine();

                if ("/exit".equalsIgnoreCase(input)) {
                    break;
                } else if (input.startsWith("/entrar ")) {
                    String salaSelec = input.substring(8);
                    comCliente.joinSala(salaSelec);
                } else if (input.startsWith("/crear ")) {
                    String salaCreada = input.substring(7);
                    comCliente.crearSala(salaCreada);
                } else if ("/listar".equalsIgnoreCase(input)) {
                    comCliente.listaSalas();
                } else if ("/salir".equalsIgnoreCase(input)) {
                    comCliente.salir();
                } else if ("/stickers".equalsIgnoreCase(input)) {
                    mostrarStickers();
                    continue;
                } else if (input.startsWith("#priv ")) {
                    String[] privParts = input.substring(6).split(" ", 2);
                    if (privParts.length == 2) {
                        comCliente.sendmsgPrivado(privParts[0], privParts[1]);
                    } else {
                        System.out.println("Formato: #priv <usuario> <mensaje>");
                    }
                } else {
                    comCliente.enviarMensajeChat(input);
                }
            }

            comCliente.stop();
            scanner.close();
            System.out.println("Sala finalizada. Hasta pronto!");

        } catch (IOException e) {
            System.err.println("Error iniciando cliente: " + e.getMessage());
        }
    }

    public void joinSala(String nomSala) {
        señalServidor("entrar:" + usuario + ":" + nomSala + ":");
        this.sala = nomSala;
    }

    public void crearSala(String nomSala) {
        señalServidor("crearSala:" + usuario + ":" + nomSala + ":");
    }

    public void enviarMensajeChat(String content) {
        String mensajeCompleto = procesarStickers(content);
        señalServidor("MSG:" + usuario + ":" + sala + ":" + mensajeCompleto);
    }

    public void sendmsgPrivado(String targetUsername, String mensaje) {
        String mensajeCompleto = procesarStickers(mensaje);
        String msg = targetUsername + ":" + mensajeCompleto;
        señalServidor("msgPrivado:" + usuario + ":" + sala + ":" + msg);
    }

    public void listaSalas() {
        señalServidor("ListarSalas:" + usuario + "::");
    }

    public void salir() {
        señalServidor("Salir:" + usuario + ":" + sala + ":");
        this.sala = "Lobby_Principal";
    }

    private void señalServidor(String message) { // Enviando banderas al servidor para peticiones del usuario
        try {
            byte[] data = message.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, serverPort);
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("Error enviando mensaje: " + e.getMessage());
        }
    }

    public void startReceiver() { // hilo para recibir mensajes del servidor
        Thread receiverThread = new Thread(() -> {
            while (banderaFin) {
                try {
                    byte[] buffer = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength());
                    respuestaServidor(message);

                } catch (IOException e) {
                    if (banderaFin) {
                        System.err.println("Error recibiendo mensaje: " + e.getMessage());
                    }
                }
            }
        });
        receiverThread.start();
    }

    private void respuestaServidor(String mensaje) {
        String[] parts = mensaje.split(":", 4);
        String type = parts[0]; // tipo (MSG, ERROR, SUCCESS, SYSTEM, LIST)
        String sender = parts[1]; // remitente
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
            case "PRIVATE":
                System.out.println("PRIVADO " + sender + ": " + content);
                break;
            default:
                System.out.println("Mensaje desconocido: " + mensaje);
        }
    }

    public void stop() {
        banderaFin = false;
        socket.close();
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