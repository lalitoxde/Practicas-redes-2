package Practica1;

import java.io.*;
import java.net.*;

public class ClienteTienda {
    public static void main(String[] args) {
        String host = "localhost"; 
        int puerto = 5000;

        try (Socket socket = new Socket(host, puerto)) {
            System.out.println("Conectado al servidor");

            
            PrintWriter salida = new PrintWriter(socket.getOutputStream(), true);
            
            BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));

          
            salida.println("que tranza servidor, soy cliente");
            salida.flush();
           
            String respuesta = entrada.readLine();
            System.out.println("Servidor dice: " + respuesta);
            socket.close();
            entrada.close();
            salida.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
