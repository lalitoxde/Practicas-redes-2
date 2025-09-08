
package Practica1;

import java.io.*;
import java.net.*;

public class sockets {
   public static void main(String[] args) {
     int puerto = 5000;

    try(ServerSocket servidor = new ServerSocket(puerto)){
    Socket socket = servidor.accept();
    System.out.println("el cliente se conecto");

    BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));

    PrintWriter salida = new PrintWriter(socket.getOutputStream());

    String mensaje= entrada.readLine();
    System.out.println("que tranza cliente, recibi tu mensaje");

    socket.close();


    }
    catch(Exception e){
        System.out.println(e);
    }
    }
   }




