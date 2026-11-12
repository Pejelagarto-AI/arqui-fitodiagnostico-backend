package com.vivero.fitodiagnostico;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prueba de arranque (sección 11): confirma que el contexto completo de Spring
 * levanta con el perfil `test` (H2 en memoria + Flyway + secretos de desarrollo), es decir, que
 * todos los beans de las cuatro capas se ensamblan sin errores de cableado.
 */
@SpringBootTest
@ActiveProfiles("test")
class FitodiagnosticoApplicationTests {

    @Test
    void elContextoLevanta() {
    }
}
