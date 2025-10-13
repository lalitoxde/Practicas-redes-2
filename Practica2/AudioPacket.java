package Practica2;
/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
/**
 *
 * @author Lenovo
 */
import java.io.Serializable;

/**
 * Representa la plantilla o el "sobre" para un único paquete de datos de audio.
 * Esta clase encapsula un pequeño trozo de datos de audio junto con metadatos
 * esenciales para su transmisión y reconstrucción, como el número de secuencia.
 * <p>
 * La clase implementa la interfaz {@link Serializable}, lo que es un requisito
 * fundamental para que los objetos de esta clase puedan ser convertidos
 * (serializados) en una secuencia de bytes. Esta conversión permite que el
 * objeto completo sea enviado a través de la red usando un ObjectOutputStream
 * y reconstruido (deserializado) en el otro extremo por un ObjectInputStream.
 */
public class AudioPacket implements Serializable {

    /**
     * El número de secuencia único de este paquete.
     * Este número es crucial para el receptor, ya que le permite ordenar los
     * paquetes en el orden correcto en que deben ser reproducidos, incluso si
     * llegan desordenados por la red. También es la base para los mecanismos
     * de control de error como Go-Back-N.
     */
    private long sequenceNumber;

    /**
     * El contenido real del paquete: un pequeño fragmento de los datos del
     * archivo MP3. Se representa como un arreglo de bytes, que es la forma
     * fundamental de manejar datos binarios como el audio.
     */
    private byte[] data;

    /**
     * Una bandera booleana que indica si este es el último paquete de la transmisión.
     * El receptor usa esta bandera para saber cuándo ha recibido la canción completa
     * y puede detener el proceso de recepción.
     */
    private boolean isLastPacket;

    /**
     * Constructor para crear una nueva instancia de AudioPacket.
     *
     * @param sequenceNumber El número de secuencia que identifica a este paquete (ej. 0, 1, 2...).
     * @param data El arreglo de bytes que contiene el fragmento de audio.
     * @param isLastPacket {@code true} si es el último paquete de la canción, {@code false} en caso contrario.
     */
    public AudioPacket(long sequenceNumber, byte[] data, boolean isLastPacket) {
        this.sequenceNumber = sequenceNumber;
        this.data = data;
        this.isLastPacket = isLastPacket;
    }

    /**
     * Método "getter" para obtener el número de secuencia del paquete.
     * Permite a otras clases consultar el número de secuencia sin poder modificarlo.
     *
     * @return El número de secuencia (long) de este paquete.
     */
    public long getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * Método "getter" para obtener los datos de audio del paquete.
     *
     * @return El arreglo de bytes (byte[]) que representa el fragmento de audio.
     */
    public byte[] getData() {
        return data;
    }

    /**
     * Método "getter" para verificar si este es el último paquete.
     *
     * @return {@code true} si este es el último paquete de la transmisión.
     */
    public boolean isLastPacket() {
        return isLastPacket;
    }
}
