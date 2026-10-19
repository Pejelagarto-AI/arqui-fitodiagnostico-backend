package com.vivero.fitodiagnostico.adaptador.entrada.arranque;

import com.vivero.fitodiagnostico.dominio.puerto.entrada.AsegurarAdministradorInicialPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Crea el primer administrador al arrancar (tabla "Datos semilla" del registro
 * de decisiones). Es un adaptador de entrada, como un controlador: recibe un
 * evento del mundo exterior (el arranque y las variables de entorno) y llama
 * a un caso de uso, {@link AsegurarAdministradorInicialPort#asegurarAdministradorInicial}.
 *
 * <p>Las credenciales vienen de {@code FITO_ADMIN_CORREO} y
 * {@code FITO_ADMIN_CONTRASENA} y nunca de un archivo del repositorio; solo el
 * perfil {@code test} trae valores de desarrollo. Con un administrador ya
 * existente las variables no hacen falta.
 */
@Component
@Order(2)
public class SembradorDeAdministrador implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SembradorDeAdministrador.class);

    /** Mismo mínimo que exige el borde web a cualquier contraseña. */
    static final int LARGO_MINIMO_CONTRASENA = 8;

    private final AsegurarAdministradorInicialPort cuentas;
    private final String correo;
    private final String contrasena;

    public SembradorDeAdministrador(AsegurarAdministradorInicialPort cuentas,
                                    @Value("${fitodiagnostico.admin.correo:}") String correo,
                                    @Value("${fitodiagnostico.admin.contrasena:}") String contrasena) {
        this.cuentas = cuentas;
        this.correo = correo;
        this.contrasena = contrasena;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!contrasena.isBlank() && contrasena.length() < LARGO_MINIMO_CONTRASENA) {
            throw new IllegalStateException("FITO_ADMIN_CONTRASENA debe tener al menos "
                    + LARGO_MINIMO_CONTRASENA + " caracteres");
        }
        if (cuentas.asegurarAdministradorInicial(correo, contrasena)) {
            log.info("se creó el primer administrador ({})", correo);
        }
    }
}
