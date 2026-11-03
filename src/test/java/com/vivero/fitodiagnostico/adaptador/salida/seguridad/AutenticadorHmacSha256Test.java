package com.vivero.fitodiagnostico.adaptador.salida.seguridad;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** El HMAC del contrato, calculado aparte con la libreria estandar: esto fija lo que el chip debe calcular. */
class AutenticadorHmacSha256Test {

    private static final String SECRETO = "c2VjcmV0by1kZS1wcnVlYmEtZGUtMzItYnl0ZXM";
    private static final String RETO = "n8Zq4m9r0b1sT2uV3wX4yZ5aB6cD7eF8gH9iJ0kL1mM=";

    private final AutenticadorHmacSha256 autenticador = new AutenticadorHmacSha256();

    private static String hmac(String clave, String mensaje) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(clave.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void aceptaBase64DelHmacSha256ConElSecretoComoStringYElRetoComoString() throws Exception {
        assertThat(autenticador.respuestaValida(SECRETO, RETO, hmac(SECRETO, RETO))).isTrue();
    }

    @Test
    void rechazaCualquierOtraRespuesta() throws Exception {
        assertThat(autenticador.respuestaValida(SECRETO, RETO, hmac("otro-secreto", RETO))).isFalse();
        assertThat(autenticador.respuestaValida(SECRETO, RETO, hmac(SECRETO, "otro-reto"))).isFalse();
        assertThat(autenticador.respuestaValida(SECRETO, RETO, "")).isFalse();
        assertThat(autenticador.respuestaValida(SECRETO, RETO, "no-es-base64-del-hmac")).isFalse();
    }

    @Test
    void ladoNuloNuncaEsValido() {
        assertThat(autenticador.respuestaValida(null, RETO, "x")).isFalse();
        assertThat(autenticador.respuestaValida(SECRETO, null, "x")).isFalse();
        assertThat(autenticador.respuestaValida(SECRETO, RETO, null)).isFalse();
    }

    @Test
    void elVectorDeReferenciaCoincideConOpenssl() throws Exception {
        // echo -n mensaje | openssl dgst -sha256 -hmac clave -binary | base64 ; si cambia, cambio el contrato con el chip.
        assertThat(hmac("clave", "mensaje")).isEqualTo("F+iEeoRjaIivWnq+CRDlCg+ijW8dNZGhdebgH8+AtBI=");
    }
}
