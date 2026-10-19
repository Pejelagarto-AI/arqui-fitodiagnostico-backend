package com.vivero.fitodiagnostico.dominio.puerto.salida;

/**
 * Puerto de salida que fabrica tokens de un solo uso (invitaciones y
 * restablecimiento de contraseña) y calcula su huella. El dominio no sabe de
 * {@code SecureRandom} ni de SHA-256, y las pruebas de aplicación usan un
 * generador determinista.
 *
 * <p>Por qué la huella vive aquí y no en cada servicio: guardar solo la huella
 * (D51) y buscar por huella son dos mitades de la misma decisión; si una
 * estuviera en el puerto y la otra en otro sitio, alguien terminaría
 * guardando el token en claro.
 */
public interface GeneradorDeTokens {

    /** Un token nuevo, impredecible y apto para ir en una URL. */
    String generar();

    /**
     * Huella determinista del texto: la misma entrada da siempre la misma
     * salida y de la salida no se recupera la entrada. Sirve para tokens y
     * para comparar credenciales sin copiarlas (D52).
     */
    String huella(String texto);
}
