package Practica4;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SegundoServidor {
    public static void iniciar(int puerto) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(puerto)) {
            System.out.println("\n>>> SERVIDOR SECUNDARIO INICIADO EN PUERTO " + puerto + " <<<\n");
            while (true) {
                Socket clienteSocket = serverSocket.accept();
                new Thread(new ManejadorCliente(clienteSocket, "Servidor 2")).start();
            }
        }
    }
}