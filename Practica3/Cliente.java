package Practica3;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javax.sound.sampled.*;

public class Cliente {
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;
    private String usuario;
    private String sala;
    private boolean banderaFin;
    private static final Map<String, String> STICKERS = new HashMap<>();

    // Variables para audio grabado
    private AudioFormat audioFormat;
    private TargetDataLine targetDataLine;
    private SourceDataLine sourceDataLine;
    private byte[] audioGrabado;
    private boolean audioListoParaEnviar = false;
    private static final int DURACION_GRABACION = 5; // segundos
    private static final int SAMPLE_RATE = 16000;
    private static final int PACKET_SIZE = 512;
    private static final int WINDOW_SIZE = 4; // Para Go-Back-N

    // Para control Go-Back-N
    private int baseSeq = 0;
    private int nextSeqNum = 0;
    private Map<Integer, byte[]> paquetesPorEnviar = new ConcurrentHashMap<>();
    private boolean transmisionActiva = false;

    public Cliente(String serverHost, int serverPort, String usuario) throws IOException {
        this.socket = new DatagramSocket();
        this.serverAddress = InetAddress.getByName(serverHost);
        this.serverPort = serverPort;
        this.usuario = usuario;
        this.sala = "Lobby_Principal";
        this.banderaFin = true;

        // Configurar formato de audio
        this.audioFormat = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

        joinSala("Lobby_Principal");
    }

    static {
        STICKERS.put(":gato:", "\n" +
                " /\\_/\\\n" +
                "( o.o )\n" +
                " > ^ <\n");

        STICKERS.put(":perro:", "\n" +
                "  /\\_/\n" +
                " (    @\\___\n" +
                " /         O\n" +
                "/   (_____/\n" +
                "/_____/   U\n");

        STICKERS.put(":corazon:", "\n" +
                " ** **\n" +
                "*******\n" +
                " *****\n" +
                "  ***\n" +
                "   *\n");

        STICKERS.put(":carafeliz:", "\n" +
                "  -----\n" +
                " | O O |\n" +
                " |  U  |\n" +
                "  -----\n");
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
            System.out.println("/grabar_audio -> Grabar 10 segundos de audio");
            System.out.println("/reproducir_audio -> Reproducir audio grabado localmente");
            System.out.println("/enviar_audio_sala -> Enviar audio grabado a la SALA");
            System.out.println("#enviar_audio_priv <usuario> -> Enviar audio grabado PRIVADO");
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
                } else if ("/grabar_audio".equalsIgnoreCase(input)) {
                    comCliente.grabarAudio();
                } else if ("/reproducir_audio".equalsIgnoreCase(input)) {
                    comCliente.reproducirAudioLocal();
                } else if ("/enviar_audio_sala".equalsIgnoreCase(input)) {
                    comCliente.enviarAudioGrabado(false, null);
                } else if (input.startsWith("#enviar_audio_priv ")) {
                    String targetUser = input.substring(19);
                    comCliente.enviarAudioGrabado(true, targetUser);
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

    // ========== MÉTODOS DE AUDIO GRABADO ==========

    public void grabarAudio() {
        if (audioListoParaEnviar) {
            System.out.println("❌ Ya hay audio grabado. Usa /reproducir_audio o /enviar_audio_sala primero.");
            return;
        }

        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
            targetDataLine = (TargetDataLine) AudioSystem.getLine(info);
            targetDataLine.open(audioFormat);
            targetDataLine.start();

            System.out.println("🎤 Grabando " + DURACION_GRABACION + " segundos...");

            // Calcular tamaño total de la grabación
            int bufferSize = (int) (audioFormat.getSampleRate() * audioFormat.getFrameSize() * DURACION_GRABACION);
            ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
            byte[] buffer = new byte[PACKET_SIZE];

            long startTime = System.currentTimeMillis();
            long endTime = startTime + (DURACION_GRABACION * 1000);

            while (System.currentTimeMillis() < endTime) {
                int bytesRead = targetDataLine.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    audioBuffer.write(buffer, 0, bytesRead);
                }
            }

            targetDataLine.stop();
            targetDataLine.close();

            audioGrabado = audioBuffer.toByteArray();
            audioListoParaEnviar = true;

            System.out.println("✅ Audio grabado: " + audioGrabado.length + " bytes");
            System.out.println("💡 Usa /reproducir_audio para escuchar o /enviar_audio_sala para compartir");

        } catch (LineUnavailableException e) {
            System.err.println("❌ Error accediendo al micrófono: " + e.getMessage());
        }
    }

    public void reproducirAudioLocal() {
        if (!audioListoParaEnviar) {
            System.out.println("❌ No hay audio grabado. Usa /grabar_audio primero.");
            return;
        }

        try {
            inicializarReproductor();
            System.out.println("🔊 Reproduciendo audio grabado...");
            sourceDataLine.write(audioGrabado, 0, audioGrabado.length);
            System.out.println("✅ Reproducción completada");

        } catch (Exception e) {
            System.err.println("❌ Error reproduciendo audio: " + e.getMessage());
        }
    }

    public void enviarAudioGrabado(boolean esPrivado, String targetUser) {
        if (!audioListoParaEnviar) {
            System.out.println("❌ No hay audio grabado. Usa /grabar_audio primero.");
            return;
        }

        if (esPrivado && (targetUser == null || targetUser.trim().isEmpty())) {
            System.out.println("❌ Debes especificar un usuario destino para audio privado");
            return;
        }

        System.out.println(
                "📤 Enviando audio grabado..." + (esPrivado ? " (PRIVADO para " + targetUser + ")" : " (SALA)"));

        // Dividir audio en paquetes y enviar con Go-Back-N
        dividirYEnviarAudio(audioGrabado, esPrivado, targetUser);
    }

    private void dividirYEnviarAudio(byte[] audioData, boolean esPrivado, String targetUser) {
        // Limpiar estado anterior
        paquetesPorEnviar.clear();
        baseSeq = 0;
        nextSeqNum = 0;
        transmisionActiva = true;

        // Dividir audio en paquetes
        int totalPaquetes = (int) Math.ceil((double) audioData.length / PACKET_SIZE);
        System.out.println("📦 Dividiendo audio en " + totalPaquetes + " paquetes...");

        // Crear paquetes
        for (int i = 0; i < totalPaquetes; i++) {
            int start = i * PACKET_SIZE;
            int end = Math.min(start + PACKET_SIZE, audioData.length);
            int length = end - start;

            byte[] paqueteAudio = Arrays.copyOfRange(audioData, start, end);
            paquetesPorEnviar.put(i, crearPaqueteAudio(i, paqueteAudio, length, totalPaquetes, esPrivado, targetUser));
        }

        // Iniciar transmisión con Go-Back-N
        iniciarTransmisionGoBackN(totalPaquetes);
    }

    private byte[] crearPaqueteAudio(int seqNum, byte[] audioData, int dataLength, int totalPaquetes,
            boolean esPrivado, String targetUser) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            // Cabecera del paquete
            dos.writeByte(1); // Tipo: audio grabado
            dos.writeUTF(usuario);
            dos.writeUTF(sala);
            dos.writeBoolean(esPrivado);
            dos.writeUTF(esPrivado ? targetUser : "");
            dos.writeInt(seqNum); // Número de secuencia
            dos.writeInt(totalPaquetes); // Total de paquetes
            dos.writeInt(dataLength); // Longitud de este paquete
            dos.write(audioData, 0, dataLength); // Datos de audio

            return baos.toByteArray();

        } catch (IOException e) {
            System.err.println("❌ Error creando paquete audio: " + e.getMessage());
            return new byte[0];
        }
    }

    private void iniciarTransmisionGoBackN(int totalPaquetes) {
        Thread transmisionThread = new Thread(() -> {
            System.out.println("🔄 Iniciando transmisión Go-Back-N (Ventana: " + WINDOW_SIZE + ")");

            Timer timeoutTimer = new Timer();

            while (baseSeq < totalPaquetes && transmisionActiva) {
                // Enviar paquetes en la ventana
                while (nextSeqNum < baseSeq + WINDOW_SIZE && nextSeqNum < totalPaquetes) {
                    byte[] paquete = paquetesPorEnviar.get(nextSeqNum);
                    if (paquete != null) {
                        enviarPaquete(paquete, nextSeqNum);
                    }
                    nextSeqNum++;
                }

                // Configurar timeout para el paquete base
                configurarTimeout(timeoutTimer, baseSeq, totalPaquetes);

                // Esperar ACKs (simulado - en realidad vendrían del servidor)
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }

            timeoutTimer.cancel();
            transmisionActiva = false;
            System.out.println("✅ Transmisión completada");

        });
        transmisionThread.start();
    }

    private void enviarPaquete(byte[] paquete, int seqNum) {
        try {
            DatagramPacket packet = new DatagramPacket(paquete, paquete.length, serverAddress, serverPort);
            socket.send(packet);
            System.out.println("📤 Enviado paquete " + seqNum + " (" + paquete.length + " bytes)");
        } catch (IOException e) {
            System.err.println("❌ Error enviando paquete " + seqNum + ": " + e.getMessage());
        }
    }

    private void configurarTimeout(Timer timer, int seqBase, int totalPaquetes) {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (transmisionActiva && baseSeq == seqBase) {
                    System.out.println("⏰ Timeout paquete " + seqBase + ", reenviando ventana...");
                    nextSeqNum = baseSeq; // Go-Back
                }
            }
        }, 2000); // Timeout de 2 segundos
    }

    private void inicializarReproductor() {
        try {
            if (sourceDataLine == null) {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
                sourceDataLine = (SourceDataLine) AudioSystem.getLine(info);
                sourceDataLine.open(audioFormat);
                sourceDataLine.start();
            }
        } catch (LineUnavailableException e) {
            System.err.println("❌ Error inicializando reproductor: " + e.getMessage());
        }
    }

    // ========== MÉTODOS EXISTENTES (modificados para audio) ==========

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

    private void señalServidor(String message) {
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
            while (banderaFin) {
                try {
                    byte[] buffer = new byte[2048];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    byte[] receivedData = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), 0, receivedData, 0, packet.getLength());

                    if (receivedData.length > 0 && receivedData[0] == 1) {
                        procesarAudioRecibido(receivedData);
                    } else {
                        String message = new String(receivedData, 0, receivedData.length);
                        respuestaServidor(message);
                    }

                } catch (IOException e) {
                    if (banderaFin) {
                        System.err.println("Error recibiendo mensaje: " + e.getMessage());
                    }
                }
            }
        });
        receiverThread.start();
    }

    private void procesarAudioRecibido(byte[] audioPacket) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(audioPacket);
            DataInputStream dis = new DataInputStream(bais);

            byte tipo = dis.readByte();
            String sender = dis.readUTF();
            String room = dis.readUTF();
            boolean esPrivado = dis.readBoolean();
            String targetUser = dis.readUTF();
            int seqNum = dis.readInt();
            int totalPaquetes = dis.readInt();
            int dataLength = dis.readInt();

            if (dataLength > 0) {
                byte[] audioData = new byte[dataLength];
                dis.readFully(audioData);

                // Aquí procesaríamos el paquete y reconstruiríamos el audio
                // Por simplicidad, lo reproducimos directamente
                if ((esPrivado && targetUser.equals(usuario)) || (!esPrivado && room.equals(sala))) {
                    System.out.println(
                            "🔊 Recibido audio de " + sender + " (paquete " + seqNum + "/" + totalPaquetes + ")");
                    reproducirAudioInmediato(audioData);
                }
            }

        } catch (IOException e) {
            System.err.println("❌ Error procesando audio recibido: " + e.getMessage());
        }
    }

    private void reproducirAudioInmediato(byte[] audioData) {
        try {
            inicializarReproductor();
            sourceDataLine.write(audioData, 0, audioData.length);
        } catch (Exception e) {
            System.err.println("❌ Error reproduciendo audio recibido: " + e.getMessage());
        }
    }

    private void respuestaServidor(String mensaje) {
        String[] parts = mensaje.split(":", 4);
        String type = parts[0];
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
            case "PRIVATE":
                System.out.println("PRIVADO " + sender + ": " + content);
                break;
            default:
                System.out.println("Mensaje desconocido: " + mensaje);
        }
    }

    public void stop() {
        banderaFin = false;
        transmisionActiva = false;
        if (socket != null)
            socket.close();
        if (sourceDataLine != null)
            sourceDataLine.close();
        if (targetDataLine != null)
            targetDataLine.close();
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