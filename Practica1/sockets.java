
package Practica1;

import java.io.*;
import java.net.*;

public class sockets {
    public static void main(String[] args) {
        int puerto = 5000; 

        try (ServerSocket servidor = new ServerSocket(puerto)) {
            System.out.println("Servidor escuchando en el puerto " + puerto);

            
            Socket socket = servidor.accept();
            System.out.println("Cliente conectado");
            
            
            BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            PrintWriter salida = new PrintWriter(socket.getOutputStream(), true);

           
            String mensaje = entrada.readLine();
            System.out.println("mensaje del cliente: " + mensaje);

            salida.println("que tranza cliente, recibí tu mensaje.");

           
            socket.close();
            entrada.close();
            salida.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}




