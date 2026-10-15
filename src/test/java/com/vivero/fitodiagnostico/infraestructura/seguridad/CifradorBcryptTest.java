package com.vivero.fitodiagnostico.infraestructura.seguridad;

import com.vivero.fitodiagnostico.dominio.modelo.Credencial;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RN10: la contraseña se guarda como hash BCrypt con sal, nunca en claro. JUnit puro. */
class CifradorBcryptTest {

    private final CifradorBcrypt cifrador = new CifradorBcrypt(4);

    @Test
    void elHashNoContieneLaContrasenaYEsBcrypt() {
        Credencial credencial = cifrador.cifrar("clave-larga-1");

        assertThat(credencial.hash()).doesNotContain("clave-larga-1").startsWith("$2");
    }

    @Test
    void laMismaContrasenaDaHashesDistintosPorLaSal() {
        assertThat(cifrador.cifrar("clave-larga-1")).isNotEqualTo(cifrador.cifrar("clave-larga-1"));
    }

    @Test
    void coincideSoloConLaContrasenaOriginal() {
        Credencial credencial = cifrador.cifrar("clave-larga-1");

        assertThat(cifrador.coincide("clave-larga-1", credencial)).isTrue();
        assertThat(cifrador.coincide("clave-larga-2", credencial)).isFalse();
        assertThat(cifrador.coincide(null, credencial)).isFalse();
    }
}
