
package Practica1;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Representa el servidor principal de la tienda (el "backend").
 * Este programa se inicia y se queda escuchando por conexiones de clientes.
 * Es responsable de mantener el inventario de productos, procesar las acciones
 * de los clientes (comprar, ver carrito, etc.) y mantener la consistencia de los datos.
 *
 * @author Lenovo // Tu autoría
 */
public class ServidorTienda {

    /**
     * La lista de productos disponibles en toda la tienda. Es la "fuente de la verdad" del inventario.
     * - Es 'static' porque solo hay UN inventario para todo el servidor.
     * - Se usa 'Collections.synchronizedList' para hacerla "thread-safe". Esto previene
     * errores si múltiples clientes (hilos) intentan modificar el stock al mismo tiempo.
     */
    private static final List<Producto> productos = Collections.synchronizedList(new ArrayList<>());

    /**
     * Método principal que arranca el servidor.
     * @param args Argumentos de la línea de comandos (no se usan).
     * @throws IOException Si ocurre un error al abrir el puerto del servidor.
     */
    public static void main(String[] args) throws IOException {
        inicializarProductos(); // Carga el inventario inicial de productos.
        
        // Abre un "puerto" de comunicación en el servidor (como una línea telefónica).
        // El servidor escuchará peticiones en el puerto 54321.
        ServerSocket socketServidor = new ServerSocket(54321);
        System.out.println("Servidor de la tienda iniciado. Esperando clientes en el puerto 54321...");

        // Bucle infinito para que el servidor siempre esté aceptando nuevos clientes.
        while (true) {
            // Esta línea es "bloqueante": el código se detiene aquí hasta que un cliente se conecta.
            // Cuando un cliente se conecta, se crea un 'Socket' para la comunicación con ese cliente.
            Socket socketCliente = socketServidor.accept();
            System.out.println("Cliente conectado desde: " + socketCliente.getInetAddress());

            // Crea un nuevo 'hilo' (un sub-proceso) para atender a este cliente.
            // Esto es crucial para que el servidor pueda atender a varios clientes a la vez.
            // Mientras este hilo atiende al cliente, el bucle principal puede esperar a otro.
            new ManejadorCliente(socketCliente).start();
        }
    }

    /**
     * Método para cargar los productos iniciales en la lista de inventario de la tienda.
     */
    public static void inicializarProductos() {
        productos.add(new Producto("Nike", "Air max 90", 2200.0, 10));
        productos.add(new Producto("Nike", "Air 270", 4000.0, 10));
        productos.add(new Producto("Adidas", "Playera estampado", 800.0, 15));
        productos.add(new Producto("Apple", "iPhone 14 pro", 28000.0, 3));
        productos.add(new Producto("Samsung", "Galaxy S23", 17000.0, 5));
        productos.add(new Producto("Adidas", "Playera REAL MADRID", 2000.0, 15));
        productos.add(new Producto("Apple", "iPhone 14", 22000.0, 3));
        productos.add(new Producto("Samsung", "Galaxy S24", 20000.0, 5));
        productos.add(new Producto("Nike", "Air force 1 LV8", 2763.0, 30));
        productos.add(new Producto("Nike", "Jordan fligh court", 2300.0, 8));
        productos.add(new Producto("Adidas", "Playera Tigres ´SIEMPRE CONTIGO´ ", 800.0, 15));
        productos.add(new Producto("Adidas", "Playera REAL MADRID 3 EQUIPACION", 1700.0, 15));
        productos.add(new Producto("Apple", "iPhone 16 pro", 35000.0, 3));
        productos.add(new Producto("Samsung", "Galaxy TABLET t10", 9000.0, 24));
        productos.add(new Producto("Apple", "iPhone 15 pro", 28000.0, 21));
        productos.add(new Producto("Samsung", "cargador", 700.0, 90));
    }

    /**
     * Clase interna que define el comportamiento de un hilo para manejar a un solo cliente.
     * Cada cliente tendrá su propia instancia de esta clase, con su propio carrito y datos.
     */
    private static class ManejadorCliente extends Thread {
        private final Socket socketCliente;
        private final Map<Producto, Integer> carrito;
        private String nombreUsuario;
        private String correoUsuario;

        /**
         * Constructor que recibe el socket del cliente para la comunicación.
         * @param socket El socket creado cuando el cliente se conectó.
         */
        public ManejadorCliente(Socket socket) {
            this.socketCliente = socket;
            this.carrito = new LinkedHashMap<>(); // Cada cliente empieza con un carrito nuevo.
        }

        /**
         * El corazón del hilo. Aquí se ejecuta la lógica de comunicación con el cliente.
         */
        @Override
        public void run() {
            // 'try-with-resources' asegura que los flujos de datos se cierren automáticamente al final.
            // ObjectOutputStream: Permite enviar objetos completos (como listas o productos) por la red.
            // ObjectInputStream: Permite recibir objetos completos desde la red.
            try (ObjectOutputStream flujoSalida = new ObjectOutputStream(socketCliente.getOutputStream());
                 ObjectInputStream flujoEntrada = new ObjectInputStream(socketCliente.getInputStream())) {

                // El servidor espera primero los datos de registro del cliente.
                nombreUsuario = (String) flujoEntrada.readObject();
                correoUsuario = (String) flujoEntrada.readObject();
                System.out.println("Usuario registrado en el servidor: " + nombreUsuario);

                String comando;
                // Bucle principal: el servidor se queda esperando comandos del cliente ("LISTAR", "COMPRAR", etc.)
                // flujoEntrada.readObject() es "bloqueante": espera hasta que el cliente envíe un comando.
                while ((comando = (String) flujoEntrada.readObject()) != null) {
                    
                    // Se usa un 'switch' para ejecutar una acción dependiendo del comando recibido.
                    switch (comando) {
                        case "LISTAR_PRODUCTOS":
                            flujoSalida.writeObject(new ArrayList<>(productos));
                            break;
                            
                        case "FILTRAR_POR_MARCA":
                            String marca = (String) flujoEntrada.readObject();
                            // Usa la API de Streams de Java 8+ para filtrar la lista de forma eficiente.
                            List<Producto> filtrados = productos.stream()
                                    .filter(p -> p.marca.equalsIgnoreCase(marca))
                                    .collect(Collectors.toList());
                            flujoSalida.writeObject(filtrados);
                            break;
                            
                        case "AGREGAR_CARRITO":
                            Producto prodParaAgregar = (Producto) flujoEntrada.readObject();
                            int cantidad = flujoEntrada.readInt();
                            String respuesta;
                            
                            // Busca el producto real en el inventario para asegurar que el stock es el correcto.
                            Producto productoReal = productos.stream().filter(p -> p.equals(prodParaAgregar)).findFirst().orElse(null);

                            if (productoReal != null && productoReal.stock >= cantidad) {
                                productoReal.stock -= cantidad; // Disminuye el stock del inventario general.
                                carrito.put(productoReal, carrito.getOrDefault(productoReal, 0) + cantidad);
                                respuesta = "OK: Producto agregado.";
                            } else {
                                respuesta = "ERROR: Stock insuficiente o producto no encontrado.";
                            }
                            flujoSalida.writeObject(respuesta);
                            break;
                            
                        case "VER_CARRITO":
                            flujoSalida.writeObject(new LinkedHashMap<>(carrito));
                            break;
                            
                        case "EDITAR_CARRITO":
                            int opcionEditar = flujoEntrada.readInt();
                            Producto productoAEditar = (Producto) flujoEntrada.readObject();
                            int cantidadEditar = flujoEntrada.readInt();
                            String respuestaEdicion = "Error desconocido.";

                            Producto prodEnCarrito = carrito.keySet().stream().filter(p -> p.equals(productoAEditar)).findFirst().orElse(null);
                            Producto prodEnTienda = productos.stream().filter(p -> p.equals(productoAEditar)).findFirst().orElse(null);
                            
                            if (cantidadEditar <= 0) {
                                respuestaEdicion = "ERROR: La cantidad debe ser un número positivo.";
                            } else if (prodEnCarrito != null && prodEnTienda != null) {
                                if (opcionEditar == 1) { // Eliminar
                                    int cantidadActual = carrito.get(prodEnCarrito);
                                    if (cantidadEditar > cantidadActual) {
                                        respuestaEdicion = "ERROR: No puedes eliminar más productos de los que tienes (" + cantidadActual + ").";
                                    } else if (cantidadEditar == cantidadActual) {
                                        carrito.remove(prodEnCarrito);
                                        prodEnTienda.stock += cantidadActual;
                                        respuestaEdicion = "OK: Producto eliminado completamente del carrito.";
                                    } else {
      
                                    carrito.put(prodEnCarrito, cantidadActual - cantidadEditar);
                                        prodEnTienda.stock += cantidadEditar;
                                        respuestaEdicion = "OK: Se eliminaron " + cantidadEditar + " unidades.";
                                    }
                                } else if (opcionEditar == 2) { // Aumentar
                                    if (cantidadEditar <= prodEnTienda.stock) {
                                        carrito.put(prodEnCarrito, carrito.get(prodEnCarrito) + cantidadEditar);
                                        prodEnTienda.stock -= cantidadEditar;
                                        respuestaEdicion = "OK: Cantidad aumentada.";
                                    } else {
                                        respuestaEdicion = "ERROR: No hay stock suficiente para aumentar. Disponible: " + prodEnTienda.stock;
                                    }
                                }
                            } else {
                                respuestaEdicion = "ERROR: El producto no fue encontrado.";
                            }
                            flujoSalida.writeObject(respuestaEdicion);
                            break;

                        case "GENERAR_TICKET":
                            flujoSalida.writeObject(new LinkedHashMap<>(carrito));
                            flujoSalida.writeObject(nombreUsuario);
                            flujoSalida.writeObject(correoUsuario);
                            break;
                            
                        case "SALIR":
                            return; // Termina el bucle y la conexión de forma ordenada.
                    }
                    // flush() se asegura de que cualquier dato pendiente se envíe inmediatamente por la red.
                    flujoSalida.flush();
                }
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Conexión perdida con el cliente.");
            } finally {
                // Este bloque se ejecuta SIEMPRE, para devolver el stock de un carrito abandonado.
                synchronized (productos) {
                    for (Map.Entry<Producto, Integer> entry : carrito.entrySet()) {
                        Producto pEnCarrito = entry.getKey();
                        int cantidadEnCarrito = entry.getValue();
                        for (Producto pEnTienda : productos) {
                            if (pEnTienda.equals(pEnCarrito)) {
                                pEnTienda.stock += cantidadEnCarrito;
                                break;
                            }
                        }
                    }
                    carrito.clear();
                }
                try {
                    socketCliente.close(); // Cierra la conexión.
                } catch (IOException e) { /* No se hace nada si ya estaba cerrada */ }
                System.out.println("Cliente desconectado: " + socketCliente.getInetAddress());
            }
        }
    }
}
