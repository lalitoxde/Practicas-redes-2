package Practica1;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Representa el cliente de la tienda (el "frontend").
 * Este es el programa con el que el usuario interactúa directamente. Se encarga de
 * mostrar los menús, leer las opciones del usuario y enviar estas peticiones
 * al servidor para que sean procesadas.
 *
 * @author Lenovo // Tu autoría
 */
public class ClienteTienda {

    /**
     * Un método "ayudante" (helper) para leer números del teclado de forma segura.
     * Utiliza un bucle y un try-catch para asegurar que el usuario ingrese un número
     * y no texto, evitando que el programa se rompa por una entrada inválida.
     * También valida que la entrada no esté vacía.
     *
     * @param leer El objeto Scanner que se está usando para leer la entrada del usuario.
     * @return Un entero válido introducido por el usuario.
     */
    public static int leerOpcionNumerica(Scanner leer) {
        while (true) { // Bucle infinito que solo se rompe con un return exitoso.
            try {
                String linea = leer.nextLine(); // Lee la línea completa para consumir el 'enter'.
                if (linea.trim().isEmpty()){
                    System.err.println("Error: No puedes dejar la opción vacía.");
                    System.out.print("Elige una opción: ");
                    continue; // Vuelve al inicio del bucle.
                }
                // Intenta convertir el texto a un número entero.
                return Integer.parseInt(linea);
            } catch (NumberFormatException e) {
                // Si la conversión falla, informa al usuario y el bucle se repite.
                System.err.println("Error: Por favor, ingresa solo un número válido.");
                System.out.print("Elige una opción: ");
            }
        }
    }

    /**
     * Método principal que inicia la aplicación del cliente.
     * @param args Argumentos de línea de comandos (no se usan).
     */
    public static void main(String[] args) {
        // --- Configuración de la Conexión ---
        String host = "localhost"; // La dirección IP del servidor. "localhost" es la propia máquina.
        int puerto = 54321;     // El puerto debe ser el mismo en el que el servidor está escuchando.

        // 'try-with-resources' es una construcción de Java que asegura que todos los recursos
        // declarados en los paréntesis (Socket, flujos, Scanner) se cierren automáticamente al final,
        // evitando fugas de memoria y recursos.
        try (Socket socket = new Socket(host, puerto);
             ObjectOutputStream flujoSalida = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream flujoEntrada = new ObjectInputStream(socket.getInputStream());
             Scanner leer = new Scanner(System.in)) {

            System.out.println("Conectado al servidor de la tienda.");

            // --- Proceso de Registro ---
            // Pide los datos al usuario y los envía inmediatamente al servidor para registrar la sesión.
            System.out.print("INGRESA TU NOMBRE: ");
            String nombreUsuario = leer.nextLine();
            while (nombreUsuario.trim().isEmpty()) {
                System.err.println("TIENES QUE INGRESAR TU NOMBRE!");
                System.out.print("INGRESA TU NOMBRE: ");
                nombreUsuario = leer.nextLine();
            }
            flujoSalida.writeObject(nombreUsuario); // Envía el nombre al servidor.

            System.out.print("INGRESA TU CORREO: ");
            String correoUsuario = leer.nextLine();
            while (correoUsuario.trim().isEmpty()) {
                System.err.println("TIENES QUE INGRESAR TU CORREO!");
                System.out.print("INGRESA TU CORREO: ");
                correoUsuario = leer.nextLine();
            }
            flujoSalida.writeObject(correoUsuario); // Envía el correo al servidor.
            flujoSalida.flush(); // Se asegura de que los datos se envíen por la red en este momento.

            System.out.println("\nBienvenido " + nombreUsuario + " (" + correoUsuario + ")");

            int opcion;
            // Bucle principal del menú. Se repite hasta que el usuario elige una opción de salida.
            do {
                System.out.println("\n--- MENU PRINCIPAL ---");
                System.out.println("1. Ver todos los productos");
                System.out.println("2. Agregar al carrito");
                System.out.println("3. Ver / Editar carrito");
                System.out.println("4. Generar ticket de compra y salir");
                System.out.println("5. Salir sin comprar");
                System.out.print("Elige una opción: ");
                opcion = leerOpcionNumerica(leer);

                switch (opcion) {
                    case 1:
                        // 1. Enviar el comando "LISTAR_PRODUCTOS" al servidor.
                        flujoSalida.writeObject("LISTAR_PRODUCTOS");
                        flujoSalida.flush();
                        
                        // 2. Esperar (se bloquea) hasta recibir la lista de productos del servidor.
                        List<Producto> productos = (List<Producto>) flujoEntrada.readObject();
                        
                        // 3. Mostrar la información recibida al usuario.
                        System.out.println("\n--- LISTA DE PRODUCTOS ---");
                        for (int i = 0; i < productos.size(); i++) {
                            Producto p = productos.get(i);
                            System.out.println((i + 1) + ". Marca: " + p.marca + " | Nombre: " + p.nombre +
                                    " | Precio: $" + p.precio + " | Stock: " + p.stock);
                        }
                        break;
                    case 2:
                        System.out.print("Escribe la marca del producto que deseas comprar: ");
                        String marca = leer.nextLine();
                        // Envía la petición para filtrar los productos por la marca ingresada.
                        flujoSalida.writeObject("FILTRAR_POR_MARCA");
                        flujoSalida.writeObject(marca);
                        flujoSalida.flush();
                        
                        List<Producto> filtrados = (List<Producto>) flujoEntrada.readObject();

                        // Valida si el servidor devolvió una lista vacía.
                        if (filtrados.isEmpty()) {
                            System.err.println("Opción inválida: No hay productos de esa marca.");
                            continue; // 'continue' salta al siguiente ciclo del menú.
                        }
                        
                        System.out.println("Productos encontrados:");
                        for (int i = 0; i < filtrados.size(); i++) {
                             Producto p = filtrados.get(i);
                             System.out.println((i + 1) + ". " + p.nombre + " | Precio: $" + p.precio + " | Stock: " + p.stock);
                        }
                        System.out.print("Selecciona el numero del producto: ");
                        int numProd = leerOpcionNumerica(leer);

                        if (numProd < 1 || numProd > filtrados.size()) {
                            System.err.println("Opción inválida.");
                            continue;
                        }
                        Producto productoSeleccionado = filtrados.get(numProd - 1);
                        System.out.print("Cantidad que deseas comprar: ");
                        int cantidad = leerOpcionNumerica(leer);
                        
                        // Envía el comando y los datos necesarios para agregar al carrito.
                        flujoSalida.writeObject("AGREGAR_CARRITO");
                        flujoSalida.writeObject(productoSeleccionado);
                        flujoSalida.writeInt(cantidad);
                        flujoSalida.flush();

                        // Espera y muestra la respuesta de confirmación/error del servidor.
                        String respuesta = (String) flujoEntrada.readObject();
                        System.out.println("Respuesta del servidor: " + respuesta);
                        break;
                    case 3:
                        flujoSalida.writeObject("VER_CARRITO");
                        flujoSalida.flush();
                        Map<Producto, Integer> carrito = (Map<Producto, Integer>) flujoEntrada.readObject();

                        if (carrito.isEmpty()) {
                            System.out.println("Carrito vacío.");
                            continue;
                        }
                        
                        // Se crea una lista a partir de las llaves del mapa para poder seleccionarlas por número.
                        List<Producto> productosEnCarrito = new ArrayList<>(carrito.keySet());
                        System.out.println("\n--- CARRITO ---");
                        for (int i = 0; i < productosEnCarrito.size(); i++) {
                           Producto p = productosEnCarrito.get(i);
                           System.out.println((i + 1) + ". " + p.nombre + " | Cantidad: " + carrito.get(p));
                        }
                        
                        System.out.println("\n¿Deseas editar el carrito?");
                        System.out.println("1. Eliminar unidades de un producto");
                        System.out.println("2. Aumentar cantidad de un producto");
                        System.out.println("3. Volver al menú");
                        System.out.print("Elige una opción: ");
                        int opcionEditar = leerOpcionNumerica(leer);

                        if (opcionEditar == 1 || opcionEditar == 2) {
                            System.out.print("Ingresa el número del producto a editar: ");
                            int numProdEditar = leerOpcionNumerica(leer);
                            if (numProdEditar < 1 || numProdEditar > productosEnCarrito.size()){
                                System.err.println("Opción inválida.");
                                continue;
                            }
                            Producto prodAEditar = productosEnCarrito.get(numProdEditar - 1);
                            
                            System.out.print("Ingresa la cantidad: ");
                            int cantEditar = leerOpcionNumerica(leer);
                            
                            // Envía al servidor el tipo de edición, el producto y la cantidad.
                            flujoSalida.writeObject("EDITAR_CARRITO");
                            flujoSalida.writeInt(opcionEditar); // 1=eliminar, 2=aumentar
                            flujoSalida.writeObject(prodAEditar);
                            flujoSalida.writeInt(cantEditar);
                            flujoSalida.flush();
                            
                            // Espera y muestra la respuesta de confirmación/error del servidor.
                            String respuestaEdicion = (String) flujoEntrada.readObject();
                            System.out.println("Respuesta del servidor: " + respuestaEdicion);
                        }
                        break;
                    case 4:
                        flujoSalida.writeObject("GENERAR_TICKET");
                        flujoSalida.flush();
                        
                        // Recibe todos los datos necesarios para imprimir el ticket.
                        Map<Producto, Integer> carritoFinal = (Map<Producto, Integer>) flujoEntrada.readObject();
                        String nombreTicket = (String) flujoEntrada.readObject();
                        String correoTicket = (String) flujoEntrada.readObject();
                        
                        if(carritoFinal.isEmpty()){
                            System.out.println("Carrito vacío. No se puede generar ticket.");
                            continue;
                        }

                        // Imprime el ticket en la consola del cliente.
                        System.out.println("\n--- TICKET DE COMPRA ---");
                        System.out.println("Cliente: " + nombreTicket);
                        System.out.println("Correo: " + correoTicket);
                        System.out.println("-------------------------");
                        double total = 0;
                        for (Map.Entry<Producto, Integer> entry : carritoFinal.entrySet()) {
                            Producto p = entry.getKey();
                            int cantTicket = entry.getValue();
                            double subtotal = cantTicket * p.precio;
                            System.out.println(p.nombre + " (" + p.marca + ") x" + cantTicket + " = $" + subtotal);
                            total += subtotal;
                        }
                        System.out.println("-------------------------");
                        System.out.println("TOTAL A PAGAR: $" + total);
                        System.out.println("--- ¡Gracias por tu compra! ---");
                        
                        // Forzamos la salida del bucle después de generar el ticket.
                        opcion = 5;
                        flujoSalida.writeObject("SALIR");
                        flujoSalida.flush();
                        break;
                    case 5:
                        // Envía el comando de salida para que el servidor cierre la conexión de forma ordenada.
                        flujoSalida.writeObject("SALIR");
                        flujoSalida.flush();
                        System.out.println("Gracias por visitar la tienda.");
                        break;
                    default:
                        System.err.println("Opción inválida.");
                }
            } while (opcion != 5);
            
        // El bloque 'catch' se ejecuta si el servidor no está disponible o si la conexión se pierde.
        } catch (Exception e) {
            System.err.println("No se pudo conectar al servidor: " + e.getMessage());
            System.err.println("Asegúrate de que el archivo ServidorTienda.java esté en ejecución.");
        }
    }
}