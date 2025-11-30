package Practica4;

import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.concurrent.*;

public class ServidorHTTP {

    private static final int PUERTO_PRINCIPAL = 9090;
    private static final int PUERTO_SECUNDARIO = 9091;

    private static ThreadPoolExecutor pool;
    private static boolean servidorSecundarioActivo = false;
    private static int tamanoPool = 0;

    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);

        // REQUISITO 3: Tamaño definido por el usuario
        System.out.print("Ingrese el tamaño del Pool de conexiones: ");
        tamanoPool = scanner.nextInt();

        pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(tamanoPool);
        ServerSocket serverSocket = new ServerSocket(PUERTO_PRINCIPAL);

        System.out.println("\n=== SERVIDOR PRINCIPAL (Puerto " + PUERTO_PRINCIPAL + ") ===");
        System.out.println((tamanoPool / 2.0) + ", se redirige.");

        while (true) {
            Socket clienteSocket = serverSocket.accept();

            int activas = pool.getActiveCount();
            System.out.println("-> Conexión entrante. Activas actualmente: " + activas);

            if (activas > (tamanoPool / 2.0)) {
                System.out.println("⚠️ CARGA ALTA (Supera la mitad). Redirigiendo al 9091...");

                if (!servidorSecundarioActivo) {
                    iniciarSegundoServidor();
                    servidorSecundarioActivo = true;
                }
                redirigirCliente(clienteSocket);
            } else {
                System.out.println("Atendiendo en Principal.");
                pool.execute(new ManejadorCliente(clienteSocket, "Servidor 1"));
            }
        }
    }

    private static void iniciarSegundoServidor() {
        new Thread(() -> {
            try {
                SegundoServidor.iniciar(PUERTO_SECUNDARIO);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void redirigirCliente(Socket clienteSocket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clienteSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clienteSocket.getOutputStream(), true);

            String inputLine = in.readLine();
            if (inputLine == null) {
                clienteSocket.close();
                return;
            }

            StringTokenizer parse = new StringTokenizer(inputLine);
            String metodo = parse.nextToken(); // "GET"
            String recurso = parse.nextToken(); // "/documento.pdf"

            System.out.println("   -> Redirigiendo petición de: " + recurso);

            // 2. ARMAR LA REDIRECCIÓN DINÁMICA
            out.println("HTTP/1.1 307 Temporary Redirect");

            out.println("Location: http://localhost:" + PUERTO_SECUNDARIO + recurso);
            out.println("Connection: close");
            out.println();

            clienteSocket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}