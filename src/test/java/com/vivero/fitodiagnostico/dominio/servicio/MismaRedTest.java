package com.vivero.fitodiagnostico.dominio.servicio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** La regla de "misma red", sin Spring: iguales, o IPv4 privadas con los mismos 24 bits. */
class MismaRedTest {

    @Test
    void dosIpIgualesEstanEnLaMismaRedAunqueSeanPublicas() {
        assertThat(MismaRed.entre("190.25.4.4", "190.25.4.4")).isTrue();
        assertThat(MismaRed.entre("0:0:0:0:0:0:0:1", "0:0:0:0:0:0:0:1")).isTrue();
    }

    @Test
    void privadasConLosMismosTresPrimerosOctetosSi() {
        assertThat(MismaRed.entre("172.20.10.2", "172.20.10.3")).isTrue();
        assertThat(MismaRed.entre("192.168.1.10", "192.168.1.200")).isTrue();
        assertThat(MismaRed.entre("10.0.5.1", "10.0.5.254")).isTrue();
    }

    @Test
    void privadasQueDifierenEnLosPrimeros24BitsNo() {
        assertThat(MismaRed.entre("172.20.10.2", "172.20.11.2")).isFalse();
        assertThat(MismaRed.entre("192.168.1.10", "192.168.2.10")).isFalse();
        assertThat(MismaRed.entre("10.0.5.1", "10.1.5.1")).isFalse();
    }

    @Test
    void publicasDistintasNoAunqueComparanTresOctetos() {
        assertThat(MismaRed.entre("190.25.4.4", "190.25.4.5")).isFalse();
        assertThat(MismaRed.entre("8.8.8.8", "8.8.8.9")).isFalse();
    }

    @Test
    void unaPrivadaYUnaPublicaNo() {
        assertThat(MismaRed.entre("192.168.1.10", "8.8.8.8")).isFalse();
    }

    @Test
    void elRangoPrivado172SoloVaDel16Al31() {
        assertThat(MismaRed.entre("172.15.0.1", "172.15.0.2")).isFalse();
        assertThat(MismaRed.entre("172.32.0.1", "172.32.0.2")).isFalse();
        assertThat(MismaRed.entre("172.31.0.1", "172.31.0.2")).isTrue();
        assertThat(MismaRed.entre("172.16.0.1", "172.16.0.2")).isTrue();
    }

    @Test
    void ipMalFormadasOVaciasNuncaCoinciden() {
        assertThat(MismaRed.entre(null, "10.0.0.1")).isFalse();
        assertThat(MismaRed.entre("10.0.0.1", null)).isFalse();
        assertThat(MismaRed.entre("", "")).isFalse();
        assertThat(MismaRed.entre("10.0.0", "10.0.0.1")).isFalse();
        assertThat(MismaRed.entre("10.0.0.256", "10.0.0.257")).isFalse();
        assertThat(MismaRed.entre("10.0.0.x", "10.0.0.y")).isFalse();
        assertThat(MismaRed.entre("10.0.0.1.5", "10.0.0.1.6")).isFalse();
        assertThat(MismaRed.entre("fe80::1", "fe80::2")).isFalse();
    }

    @Test
    void ignoraEspaciosAlrededor() {
        assertThat(MismaRed.entre(" 172.20.10.2 ", "172.20.10.3")).isTrue();
    }
}
