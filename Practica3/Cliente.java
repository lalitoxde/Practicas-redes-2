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

    // MÚLTIPLES AUDIOS - REEMPLAZO DE LAS VARIABLES VIEJAS
    private List<byte[]> audiosGrabados = new ArrayList<>();
    private int audioActualIndex = -1;

    private static final int DURACION_GRABACION = 5; // segundos
    private static final int SAMPLE_RATE = 16000;
    private static final int PACKET_SIZE = 512;
    private static final int WINDOW_SIZE = 4; // Para Go-Back-N

    // Para control Go-Back-N
    private int baseSeq = 0;
    private int nextSeqNum = 0;
    private Map<Integer, byte[]> paquetesPorEnviar = new ConcurrentHashMap<>();
    private boolean transmisionActiva = false;
    private Map<String, List<byte[]>> audiosPrivadosRecibidos = new ConcurrentHashMap<>();
    private Map<String, String> remitentesAudiosPrivados = new ConcurrentHashMap<>();

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
            System.out.println("/audio -> Grabar 5 segundos de audio");
            System.out.println("/reproducirAudio -> Reproducir audio grabado localmente");
            System.out.println("/enviar_audio_sala -> Enviar audio grabado a la SALA");
            System.out.println("#enviar_audio_priv <usuario> -> Enviar audio grabado PRIVADO");
            System.out.println("#escucharAudios -> Ver lista de audios PRIVADOS");
            System.out.println("#reproducir_audio <numero> -> Reproducir un audio grabado PRIVADO");
            System.out.println("#limpiarAudios -> Quitar audios PRIVADOS de la lista");
            System.out.println("/salir -> Salir de la sala actual");
            System.out.println("/exit -> Salir del chat");

            // NUEVOS COMANDOS PARA MÚLTIPLES AUDIOS
            System.out.println("/listar_audios -> Mostrar todos los audios grabados");
            System.out.println("/seleccionar_audio <numero> -> Seleccionar un audio específico");
            System.out.println("/eliminar_audio <numero> -> Eliminar un audio grabado");
            System.out.println("/limpiar_audios_locales -> Eliminar todos los audios grabados");
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
                } else if ("/audio".equalsIgnoreCase(input)) {
                    comCliente.grabarAudio();
                } else if ("/reproducirAudio".equalsIgnoreCase(input)) {
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
                } else if ("#escucharAudios".equalsIgnoreCase(input)) {
                    comCliente.mostrarAudiosPrivados();
                } else if (input.startsWith("#reproducir_audio ")) {
                    String numeroAudio = input.substring(18);
                    comCliente.reproducirAudioPrivado(numeroAudio);
                } else if ("#limpiarAudios".equalsIgnoreCase(input)) {
                    comCliente.limpiarAudiosPrivados();

                    // NUEVOS COMANDOS PARA MÚLTIPLES AUDIOS
                } else if ("/listar_audios".equalsIgnoreCase(input)) {
                    comCliente.listarAudiosGrabados();
                } else if (input.startsWith("/seleccionar_audio ")) {
                    String numeroAudio = input.substring(19);
                    comCliente.seleccionarAudio(numeroAudio);
                } else if (input.startsWith("/eliminar_audio ")) {
                    String numeroAudio = input.substring(16);
                    comCliente.eliminarAudio(numeroAudio);
                } else if ("/limpiar_audios_locales".equalsIgnoreCase(input)) {
                    comCliente.limpiarTodosAudios();

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

    // ========== MÉTODOS DE AUDIO GRABADO (MÚLTIPLES) ==========

    public void grabarAudio() {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
            targetDataLine = (TargetDataLine) AudioSystem.getLine(info);
            targetDataLine.open(audioFormat);
            targetDataLine.start();

            System.out.println("Grabando " + DURACION_GRABACION + " segundos...");

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

            byte[] nuevoAudio = audioBuffer.toByteArray();
            audiosGrabados.add(nuevoAudio);
            audioActualIndex = audiosGrabados.size() - 1;

            System.out.println("✅ Audio #" + (audioActualIndex + 1) + " grabado: " + nuevoAudio.length + " bytes");
            System.out.println("📊 Total de audios grabados: " + audiosGrabados.size());
            System.out.println("Usa /reproducirAudio para escuchar el último o /enviar_audio_sala para compartir");

        } catch (LineUnavailableException e) {
            System.err.println("Error accediendo al micrófono: " + e.getMessage());
        }
    }

    public void reproducirAudioLocal() {
        if (audiosGrabados.isEmpty()) {
            System.out.println("No hay audios grabados. Usa /audio primero.");
            return;
        }

        try {
            inicializarReproductor();
            byte[] audio = getAudioActual();
            System.out.println(
                    "Reproduciendo audio #" + (getIndiceAudioActual() + 1) + " de " + audiosGrabados.size() + "...");
            sourceDataLine.write(audio, 0, audio.length);
            System.out.println("Reproducción completada");

        } catch (Exception e) {
            System.err.println("Error reproduciendo audio: " + e.getMessage());
        }
    }

    public void enviarAudioGrabado(boolean esPrivado, String targetUser) {
        if (audiosGrabados.isEmpty()) {
            System.out.println("No hay audios grabados. Usa /audio primero.");
            return;
        }

        if (esPrivado && (targetUser == null || targetUser.trim().isEmpty())) {
            System.out.println("Debes especificar un usuario destino para audio privado");
            return;
        }

        // Si hay múltiples audios y ninguno está seleccionado, mostrar la lista
        if (audiosGrabados.size() > 1 && audioActualIndex == -1) {
            System.out.println("📢 Tienes múltiples audios grabados. Selecciona uno:");
            listarAudiosGrabados();
            System.out.println("Usa: /seleccionar_audio <número> y luego vuelve a enviar");
            return;
        }

        byte[] audioParaEnviar = getAudioActual();

        System.out.println("🎵 Enviando audio #" + (getIndiceAudioActual() + 1) + " de " + audiosGrabados.size() +
                (esPrivado ? " (PRIVADO para " + targetUser + ")" : " (SALA " + sala + ")"));
        System.out.println("   Tamaño: " + audioParaEnviar.length + " bytes");

        // Dividir audio en paquetes y enviar con Go-Back-N
        dividirYEnviarAudio(audioParaEnviar, esPrivado, targetUser);
    }

    // ========== MÉTODOS PARA GESTIONAR MÚLTIPLES AUDIOS ==========

    public void listarAudiosGrabados() {
        if (audiosGrabados.isEmpty()) {
            System.out.println("No hay audios grabados.");
            return;
        }

        System.out.println("\n🎧 AUDIOS GRABADOS:");
        System.out.println("══════════════════════════════");
        for (int i = 0; i < audiosGrabados.size(); i++) {
            byte[] audio = audiosGrabados.get(i);
            String seleccionado = (i == audioActualIndex) ? " ✅ ACTUAL" : "";
            System.out.println((i + 1) + ". Audio - " + audio.length + " bytes" + seleccionado);
        }
        System.out.println("══════════════════════════════");
        System.out.println("Usa /seleccionar_audio <número> para elegir cual reproducir/enviar");
        System.out.println("Usa /eliminar_audio <número> para borrar un audio");
    }

    public void seleccionarAudio(String numeroStr) {
        try {
            int numero = Integer.parseInt(numeroStr) - 1;
            if (numero >= 0 && numero < audiosGrabados.size()) {
                audioActualIndex = numero;
                System.out.println("✅ Audio #" + (numero + 1) + " seleccionado");
            } else {
                System.out.println("Número de audio inválido. Usa /listar_audios para ver la lista.");
            }
        } catch (NumberFormatException e) {
            System.out.println("Número inválido. Usa: /seleccionar_audio <número>");
        }
    }

    public void eliminarAudio(String numeroStr) {
        try {
            int numero = Integer.parseInt(numeroStr) - 1;
            if (numero >= 0 && numero < audiosGrabados.size()) {
                audiosGrabados.remove(numero);
                if (audioActualIndex == numero) {
                    audioActualIndex = -1;
                } else if (audioActualIndex > numero) {
                    audioActualIndex--;
                }
                System.out.println("🗑️ Audio #" + (numero + 1) + " eliminado");
            } else {
                System.out.println("Número de audio inválido. Usa /listar_audios para ver la lista.");
            }
        } catch (NumberFormatException e) {
            System.out.println("Número inválido. Usa: /eliminar_audio <número>");
        }
    }

    public void limpiarTodosAudios() {
        int cantidad = audiosGrabados.size();
        audiosGrabados.clear();
        audioActualIndex = -1;
        System.out.println("🗑️ " + cantidad + " audios eliminados");
    }

    // Métodos auxiliares para audio
    public boolean tieneMultiplesAudios() {
        return audiosGrabados.size() > 1;
    }

    private byte[] getAudioActual() {
        if (audioActualIndex == -1 && !audiosGrabados.isEmpty()) {
            return audiosGrabados.get(audiosGrabados.size() - 1);
        }
        return (audioActualIndex >= 0) ? audiosGrabados.get(audioActualIndex) : null;
    }

    private int getIndiceAudioActual() {
        if (audioActualIndex == -1 && !audiosGrabados.isEmpty()) {
            return audiosGrabados.size() - 1;
        }
        return audioActualIndex;
    }

    // ========== MÉTODOS EXISTENTES (sin cambios mayores) ==========

    private void dividirYEnviarAudio(byte[] audioData, boolean esPrivado, String targetUser) {
        // Limpiar estado anterior
        paquetesPorEnviar.clear();
        baseSeq = 0;
        nextSeqNum = 0;
        transmisionActiva = true;

        // Dividir audio en paquetes
        int totalPaquetes = (int) Math.ceil((double) audioData.length / PACKET_SIZE);
        System.out.println("Dividiendo audio en " + totalPaquetes + " paquetes...");

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
            System.err.println("Error creando paquete audio: " + e.getMessage());
            return new byte[0];
        }
    }

    private void iniciarTransmisionGoBackN(int totalPaquetes) {
        Thread transmisionThread = new Thread(() -> {
            System.out.println("Iniciando transmisión Go-Back-N (Ventana: " + WINDOW_SIZE + ")");

            Timer timeoutTimer = new Timer();
            final boolean[] timeoutActivo = { false };

            while (baseSeq < totalPaquetes && transmisionActiva) {
                // Enviar paquetes en la ventana actual
                while (nextSeqNum < baseSeq + WINDOW_SIZE && nextSeqNum < totalPaquetes) {
                    byte[] paquete = paquetesPorEnviar.get(nextSeqNum);
                    if (paquete != null) {
                        enviarPaquete(paquete, nextSeqNum);
                    }
                    nextSeqNum++;
                }

                // Configurar timeout solo si no hay uno activo
                if (!timeoutActivo[0]) {
                    timeoutActivo[0] = true;
                    final int baseActual = baseSeq;

                    TimerTask timeoutTask = new TimerTask() {
                        @Override
                        public void run() {
                            if (transmisionActiva && baseSeq == baseActual) {
                                System.out.println("TIMEOUT paquete " + baseActual + ", reenviando ventana...");
                                nextSeqNum = baseActual; // Go-Back
                            }
                            timeoutActivo[0] = false;
                        }
                    };

                    timeoutTimer.schedule(timeoutTask, 2000); // 2 segundos
                }

                // Esperar a que baseSeq avance (por los ACKs)
                try {
                    long startWait = System.currentTimeMillis();
                    int baseInicial = baseSeq;

                    while (transmisionActiva && baseSeq == baseInicial) {
                        Thread.sleep(50); // Espera más corta

                        // Timeout de espera para ACKs
                        if (System.currentTimeMillis() - startWait > 3000) {
                            System.out.println("Timeout esperando ACKs, continuando...");
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }

            timeoutTimer.cancel();
            transmisionActiva = false;

            if (baseSeq >= totalPaquetes) {
                System.out.println("Transmisión COMPLETADA - " + totalPaquetes + " paquetes enviados");
            } else {
                System.out.println("Transmisión interrumpida en paquete " + baseSeq + " de " + totalPaquetes);
            }

            // Limpiar recursos
            paquetesPorEnviar.clear();

        });
        transmisionThread.start();
    }

    private void procesarACK(String ackMensaje) {
        try {
            String[] parts = ackMensaje.split(":");
            if (parts.length >= 4) {
                String usuarioACK = parts[1];
                int seqNum = Integer.parseInt(parts[3]);

                // Solo procesar si es para nosotros
                if (usuarioACK.equals(usuario) && transmisionActiva) {
                    if (seqNum >= baseSeq) {
                        baseSeq = seqNum + 1;
                    }
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("ACK mal formado: " + ackMensaje);
        }
    }

    private void enviarPaquete(byte[] paquete, int seqNum) {
        try {
            DatagramPacket packet = new DatagramPacket(paquete, paquete.length, serverAddress, serverPort);
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("Error enviando paquete " + seqNum + ": " + e.getMessage());
        }
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
            System.err.println("Error inicializando reproductor: " + e.getMessage());
        }
    }

    // ========== MÉTODOS DE AUDIO PRIVADO (sin cambios) ==========

    private void guardarAudioPrivado(String remitente, byte[] audioData, int seqNum, int totalPaquetes) {
        // Crear una clave única para este audio específico
        String claveAudio = remitente + "_audio_" + System.currentTimeMillis();

        // Buscar si ya existe una sesión de audio en progreso para este remitente
        boolean sesionExistente = false;
        for (String claveExistente : new ArrayList<>(audiosPrivadosRecibidos.keySet())) {
            if (claveExistente.startsWith(remitente + "_audio_")) {
                List<byte[]> paquetesExistente = audiosPrivadosRecibidos.get(claveExistente);
                // Verificar si esta sesión aún no está completa y tiene el mismo total de
                // paquetes
                if (paquetesExistente.size() <= totalPaquetes && !esAudioCompleto(paquetesExistente)) {
                    claveAudio = claveExistente;
                    sesionExistente = true;
                    break;
                }
            }
        }

        if (!sesionExistente) {
            // Nueva sesión de audio - inicializar con el tamaño total esperado
            List<byte[]> nuevaLista = new ArrayList<>();
            // Inicializar con nulls para reservar espacios
            for (int i = 0; i < totalPaquetes; i++) {
                nuevaLista.add(null);
            }
            audiosPrivadosRecibidos.put(claveAudio, nuevaLista);
            remitentesAudiosPrivados.put(claveAudio, remitente);
            System.out
                    .println("🎙️ Nuevo audio privado iniciado de " + remitente + " (" + totalPaquetes + " paquetes)");
        }

        List<byte[]> paquetesAudio = audiosPrivadosRecibidos.get(claveAudio);

        // Si la lista es más pequeña que el seqNum, expandirla
        while (paquetesAudio.size() <= seqNum) {
            paquetesAudio.add(null);
        }

        // Colocar el paquete en su posición correcta
        paquetesAudio.set(seqNum, audioData);

        // Contar paquetes recibidos
        int paquetesRecibidos = 0;
        for (byte[] paquete : paquetesAudio) {
            if (paquete != null) {
                paquetesRecibidos++;
            }
        }

        System.out.println("📦 Audio de " + remitente + " [Paquete " + (seqNum + 1) + "/" + totalPaquetes +
                "] - Progreso: " + paquetesRecibidos + "/" + totalPaquetes);

        // Verificar si el audio está completo
        if (paquetesRecibidos >= totalPaquetes && paquetesRecibidos == paquetesAudio.size()) {
            System.out.println("✅ AUDIO COMPLETO de " + remitente + " - Listo para reproducir");
            System.out.println("   Usa #escucharAudios para ver y #reproducir_audio <numero> para escuchar");
        }
    }

    // Método auxiliar para verificar si un audio está completo
    private boolean esAudioCompleto(List<byte[]> paquetes) {
        for (byte[] paquete : paquetes) {
            if (paquete == null) {
                return false;
            }
        }
        return !paquetes.isEmpty();
    }

    public void mostrarAudiosPrivados() {
        // Filtrar solo audios completos
        Map<String, List<byte[]>> audiosCompletos = new LinkedHashMap<>();
        Map<String, String> remitentesCompletos = new LinkedHashMap<>();

        for (String clave : audiosPrivadosRecibidos.keySet()) {
            List<byte[]> paquetes = audiosPrivadosRecibidos.get(clave);
            String remitente = remitentesAudiosPrivados.get(clave);

            // Usar el método auxiliar para verificar si está completo
            if (esAudioCompleto(paquetes)) {
                audiosCompletos.put(clave, paquetes);
                remitentesCompletos.put(clave, remitente);
            }
        }

        if (audiosCompletos.isEmpty()) {
            System.out.println("📭 No hay audios privados COMPLETOS pendientes");
            // Mostrar audios en progreso
            int audiosEnProgreso = 0;
            for (List<byte[]> paquetes : audiosPrivadosRecibidos.values()) {
                if (!esAudioCompleto(paquetes) && !paquetes.isEmpty()) {
                    audiosEnProgreso++;
                }
            }
            if (audiosEnProgreso > 0) {
                System.out.println("   📥 " + audiosEnProgreso + " audios en progreso...");
            }
            return;
        }

        System.out.println("\n🎧 AUDIOS PRIVADOS COMPLETOS:");
        System.out.println("══════════════════════════════");

        int index = 1;
        for (String clave : audiosCompletos.keySet()) {
            String remitente = remitentesCompletos.get(clave);
            List<byte[]> paquetes = audiosCompletos.get(clave);

            System.out.println(index + ". De: " + remitente + " - " + paquetes.size() + " paquetes de audio");
            index++;
        }

        System.out.println("══════════════════════════════");
        System.out.println("Usa #reproducir_audio <número> para escuchar");
        System.out.println("Usa #limpiarAudios para borrar todos");
        System.out.println();
    }

    public void reproducirAudioPrivado(String numeroStr) {
        try {
            int numero = Integer.parseInt(numeroStr) - 1;

            // Filtrar solo audios completos (igual que en mostrarAudiosPrivados)
            List<String> clavesCompletas = new ArrayList<>();
            List<String> remitentesCompletos = new ArrayList<>();
            List<List<byte[]>> listaPaquetesCompletos = new ArrayList<>();

            for (String clave : audiosPrivadosRecibidos.keySet()) {
                List<byte[]> paquetes = audiosPrivadosRecibidos.get(clave);
                String remitente = remitentesAudiosPrivados.get(clave);

                boolean completo = true;
                for (byte[] paquete : paquetes) {
                    if (paquete == null) {
                        completo = false;
                        break;
                    }
                }

                if (completo && !paquetes.isEmpty()) {
                    clavesCompletas.add(clave);
                    remitentesCompletos.add(remitente);
                    listaPaquetesCompletos.add(paquetes);
                }
            }

            if (numero < 0 || numero >= clavesCompletas.size()) {
                System.out.println("Número de audio inválido. Usa #escucharAudios para ver la lista.");
                return;
            }

            String remitente = remitentesCompletos.get(numero);
            List<byte[]> paquetesAudio = listaPaquetesCompletos.get(numero);

            System.out.println("🔊 Reproduciendo audio privado de " + remitente + "...");

            // Combinar todos los paquetes en un solo audio
            int totalBytes = paquetesAudio.stream().mapToInt(p -> p.length).sum();
            ByteArrayOutputStream audioCompleto = new ByteArrayOutputStream(totalBytes);

            for (byte[] paquete : paquetesAudio) {
                if (paquete != null) {
                    audioCompleto.write(paquete, 0, paquete.length);
                }
            }

            byte[] audioData = audioCompleto.toByteArray();
            System.out.println("   Tamaño del audio: " + audioData.length + " bytes");

            reproducirAudioInmediato(audioData);
            System.out.println("✅ Audio reproducido completamente");

        } catch (NumberFormatException e) {
            System.out.println("Número inválido. Usa: #reproducir_audio <número>");
        } catch (Exception e) {
            System.err.println("Error procesando audio: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void limpiarAudiosPrivados() {
        int cantidad = audiosPrivadosRecibidos.size();
        audiosPrivadosRecibidos.clear();
        remitentesAudiosPrivados.clear();
        System.out.println("🗑️ " + cantidad + " audios privados eliminados");
    }

    // ========== MÉTODOS EXISTENTES DEL CHAT ==========

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
                if (esPrivado) {
                    // AUDIO PRIVADO: Guardar sin reproducir
                    if (targetUser.equals(usuario)) {
                        guardarAudioPrivado(sender, audioData, seqNum, totalPaquetes);
                    }
                } else {
                    // AUDIO PÚBLICO: Reproducir inmediatamente
                    if (room.equals(sala)) {
                        System.out.println("Audio de " + sender);
                        reproducirAudioInmediato(audioData);
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("Error procesando audio recibido: " + e.getMessage());
        }
    }

    private void reproducirAudioInmediato(byte[] audioData) {
        try {
            inicializarReproductor();
            sourceDataLine.write(audioData, 0, audioData.length);
        } catch (Exception e) {
            System.err.println("Error reproduciendo audio recibido: " + e.getMessage());
        }
    }

    private void respuestaServidor(String mensaje) {
        // PRIMERO verificar si es un ACK
        if (mensaje.startsWith("ACK:")) {
            procesarACK(mensaje);
            return;
        }
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