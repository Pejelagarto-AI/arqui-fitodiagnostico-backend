package com.vivero.fitodiagnostico;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Prueba de arranque (sección 11): confirma que el contexto completo de Spring
 * levanta con el perfil por defecto (H2 en memoria + Flyway), es decir, que
 * todos los beans de las cuatro capas se ensamblan sin errores de cableado.
 */
@SpringBootTest
class FitodiagnosticoApplicationTests {

    @Test
    void elContextoLevanta() {
    }
}
