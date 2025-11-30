package Practica4;

import java.io.*;
import java.net.*;
import java.util.*;

public class ManejadorCliente implements Runnable {

    private Socket socket;
    private String nombreServidor;

    public ManejadorCliente(Socket socket, String nombreServidor) {
        this.socket = socket;
        this.nombreServidor = nombreServidor;
    }

    @Override
    public void run() {
        try {

            if (nombreServidor.equals("Servidor 1")) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                } // 10 segundos
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream());
            BufferedOutputStream dataOut = new BufferedOutputStream(socket.getOutputStream());

            String inputLine = in.readLine();
            if (inputLine == null)
                return;

            StringTokenizer parse = new StringTokenizer(inputLine);
            String metodo = parse.nextToken().toUpperCase();
            String recurso = parse.nextToken();

            if (recurso.contains("favicon.ico"))
                return;

            System.out.println("   Procesando " + metodo + " " + recurso + " en " + nombreServidor);

            switch (metodo) {
                case "GET":
                    procesarGET(recurso, out, dataOut);
                    break;
                case "POST":
                    enviarRespuestaSimple(out, "200 OK", "<h1>POST Recibido</h1>");
                    procesarPOST(in, out);
                    break;
                case "PUT":
                    enviarRespuestaSimple(out, "201 Created", "<h1>Archivo creado </h1>");
                    procesarPUT(recurso, in, out);
                    break;
                case "DELETE":
                    enviarRespuestaSimple(out, "200 OK", "<h1>Archivo eliminado </h1>");
                    procesarDELETE(recurso, out);
                    break;
                default:
                    enviarRespuestaSimple(out, "501 Not Implemented", "<h1>Metodo no soportado</h1>");
            }

        } catch (IOException e) {
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }

    private void procesarDELETE(String recurso, PrintWriter out) {
        if (recurso.startsWith("/"))
            recurso = recurso.substring(1);

        File archivo = new File(recurso);

        System.out.println("   -> Buscando archivo en: " + archivo.getAbsolutePath());

        if (archivo.exists() && archivo.delete()) {
            System.out.println("   -> >>> EXITO: Archivo borrado real.");
            out.println("HTTP/1.1 200 OK");
            out.println();
            out.flush();
        } else {
            System.out.println("   -> >>> ERROR: No se pudo borrar.");
            out.println("HTTP/1.1 404 Not Found");
            out.println();
            out.flush();
        }
    }

    private void procesarGET(String recurso, PrintWriter out, BufferedOutputStream dataOut) throws IOException {
        if (recurso.equals("/"))
            recurso = "index.html";
        if (recurso.startsWith("/"))
            recurso = recurso.substring(1);

        File archivo = new File(recurso);

        if (archivo.exists() && !archivo.isDirectory()) {
            String mime = getContentType(recurso);
            int fileLength = (int) archivo.length();

            out.println("HTTP/1.1 200 OK");
            out.println("Content-type: " + mime);
            out.println("Content-length: " + fileLength);
            out.println();
            out.flush();

            FileInputStream fileIn = new FileInputStream(archivo);
            byte[] fileData = new byte[fileLength];
            fileIn.read(fileData);
            fileIn.close();

            dataOut.write(fileData, 0, fileLength);
            dataOut.flush();
        } else {
            enviarRespuestaSimple(out, "404 Not Found", "<h1>404 - Archivo no encontrado</h1>");
        }
    }

    private void procesarPOST(BufferedReader in, PrintWriter out) throws IOException {
        // En un servidor real, aquí leeríamos el 'body' del mensaje.
        // Para la práctica, basta con detectar el verbo y responder.
        System.out.println(">>> PETICIÓN POST RECIBIDA: Datos simulados aceptados.");

        out.println("HTTP/1.1 200 OK");
        out.println("Content-type: text/html");
        out.println();
        out.println("<html><body><h1>Datos recibidos correctamente (POST)</h1></body></html>");
        out.flush();
    }

    private void procesarPUT(String recurso, BufferedReader in, PrintWriter out) {
        try {

            if (recurso.startsWith("/"))
                recurso = recurso.substring(1);

            File archivo = new File(recurso);
            boolean creado = false;

            if (archivo.createNewFile()) {
                System.out.println(">>> EXITO: Archivo creado: " + recurso);
                creado = true;
            } else {
                System.out.println(">>> AVISO: El archivo ya existía, se actualizó.");
            }

            FileWriter escritor = new FileWriter(archivo);
            escritor.write("Este archivo fue creado via PUT el: " + new Date());
            escritor.close();

            if (creado) {

                out.println("HTTP/1.1 201 Created");
            } else {

                out.println("HTTP/1.1 200 OK");
            }

            out.println("Content-type: text/html");
            out.println();
            out.println("<html><body><h1>Operacion PUT exitosa: " + recurso + "</h1></body></html>");
            out.flush();

        } catch (IOException e) {
            e.printStackTrace();
            // Enviar error 500 si falla el disco duro
            out.println("HTTP/1.1 500 Internal Server Error");
            out.println();
            out.flush();
        }
    }

    private void enviarRespuestaSimple(PrintWriter out, String estado, String html) {
        out.println("HTTP/1.1 " + estado);
        out.println("Content-type: text/html");
        out.println();
        out.println("<html><body>" + html + "</body></html>");
        out.flush();
    }

    private String getContentType(String fileRequested) {

        if (fileRequested.endsWith(".htm") || fileRequested.endsWith(".html"))
            return "text/html";
        if (fileRequested.endsWith(".css"))
            return "text/css"; // <--- Para estilos
        if (fileRequested.endsWith(".js"))
            return "application/javascript"; // <--- Para scripts
        if (fileRequested.endsWith(".txt"))
            return "text/plain";

        // Imágenes
        if (fileRequested.endsWith(".jpg") || fileRequested.endsWith(".jpeg"))
            return "image/jpeg";
        if (fileRequested.endsWith(".png"))
            return "image/png"; // <--- Para PNGs transparentes
        if (fileRequested.endsWith(".gif"))
            return "image/gif";
        if (fileRequested.endsWith(".ico"))
            return "image/x-icon"; // <--- Para el icono de la pestaña

        // Documentos y Datos
        if (fileRequested.endsWith(".json"))
            return "application/json";
        if (fileRequested.endsWith(".pdf"))
            return "application/pdf";
        if (fileRequested.endsWith(".xml"))
            return "application/xml";
        if (fileRequested.endsWith(".pdf"))
            return "application/pdf";

        // Multimedia
        if (fileRequested.endsWith(".mp4"))
            return "video/mp4";
        if (fileRequested.endsWith(".mp3"))
            return "audio/mpeg";

        return "application/octet-stream";
    }
}