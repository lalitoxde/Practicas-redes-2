package Practica1;
import java.io.Serializable;
import java.util.Objects;
/**
 *
 * @author Lenovo
 */
public class Producto implements Serializable {
    String marca;
    String nombre;
    double precio;
    int stock;

    Producto(String marca, String nombre, double precio, int stock) {
        this.marca = marca;
        this.nombre = nombre;
        this.precio = precio;
        this.stock = stock;
    }

    // --- MÉTODOS AÑADIDOS (MUY IMPORTANTES) ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Producto producto = (Producto) o;
        return marca.equals(producto.marca) && nombre.equals(producto.nombre);
    }

    @Override
    public int hashCode() {
        return Objects.hash(marca, nombre);
    }
}