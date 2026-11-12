package com.vivero.fitodiagnostico.aplicacion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** El acuerdo con el front sobre las pantallas de los enlaces (D53), sin Spring. */
class RutasDelFrontTest {

    @Test
    void cadaPantallaTieneSuRutaYElTokenVaCodificado() {
        assertThat(RutasDelFront.invitacion("abc-DEF_123")).isEqualTo("/invitacion?token=abc-DEF_123");
        assertThat(RutasDelFront.restablecimiento("abc")).isEqualTo("/restablecer?token=abc");
        assertThat(RutasDelFront.invitacion("a b&c=d")).isEqualTo("/invitacion?token=a+b%26c%3Dd");
    }

    @Test
    void laUrlBaseSePuedeDarConOSinBarraFinal() {
        assertThat(RutasDelFront.absoluta("http://front.local", "/invitacion?token=t")).isEqualTo("http://front.local/invitacion?token=t");
        assertThat(RutasDelFront.absoluta("http://front.local/", "/invitacion?token=t")).isEqualTo("http://front.local/invitacion?token=t");
    }
}
