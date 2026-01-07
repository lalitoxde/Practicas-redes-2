/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Practica5;

/**
 *
 * @author Lenovo
 */
public class Conversion {

    public static String textoABits(String texto) {
        StringBuilder binario = new StringBuilder();
        byte[] bytes = texto.getBytes();
        for (byte b : bytes) {
            int val = b;
            for (int i = 0; i < 8; i++) {
                binario.append((val & 128) == 0 ? 0 : 1);
                val <<= 1;
            }
        }
        return binario.toString();
    }

    public static String bitsATexto(String binario) {
        StringBuilder texto = new StringBuilder();

        for (int i = 0; i < binario.length(); i += 8) {
            String byteStr = binario.substring(i, Math.min(i + 8, binario.length()));
            int charCode = Integer.parseInt(byteStr, 2);
            texto.append((char) charCode);
        }
        return texto.toString();
    }
}
