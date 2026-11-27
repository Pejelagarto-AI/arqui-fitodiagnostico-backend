package com.vivero.fitodiagnostico.config;

import com.vivero.fitodiagnostico.dominio.modelo.Periodo;
import com.vivero.fitodiagnostico.dominio.servicio.ReglaDeLuzAcumulada;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** La zona horaria y la franja del día llegan al dominio desde la configuración (fase 16, D206). */
class ConfiguracionDominioTest {

    private final ConfiguracionDominio configuracion = new ConfiguracionDominio();

    @Test
    void conLosValoresPorDefectoElDiaVaDeSeisAlasSeisDeBogota() {
        ReglaDeLuzAcumulada regla = configuracion.reglaDeLuzAcumulada("America/Bogota", "06:00", "18:00");

        assertThat(regla.periodo(Instant.parse("2026-11-17T10:59:00Z"))).isEqualTo(Periodo.NOCHE);   // 05:59
        assertThat(regla.periodo(Instant.parse("2026-11-17T11:00:00Z"))).isEqualTo(Periodo.DIA);     // 06:00
        assertThat(regla.periodo(Instant.parse("2026-11-17T23:00:00Z"))).isEqualTo(Periodo.NOCHE);   // 18:00
    }

    @Test
    void otraZonaOtraFranjaPorConfiguracion() {
        ReglaDeLuzAcumulada regla = configuracion.reglaDeLuzAcumulada("America/Mexico_City", "07:00", "19:00");

        assertThat(regla.periodo(Instant.parse("2026-11-17T12:59:00Z"))).isEqualTo(Periodo.NOCHE);   // 06:59 local
        assertThat(regla.periodo(Instant.parse("2026-11-17T13:00:00Z"))).isEqualTo(Periodo.DIA);     // 07:00 local
    }
}
