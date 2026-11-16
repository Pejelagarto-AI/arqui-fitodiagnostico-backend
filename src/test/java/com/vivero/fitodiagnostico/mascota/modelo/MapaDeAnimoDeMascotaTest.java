package com.vivero.fitodiagnostico.mascota.modelo;

import com.vivero.fitodiagnostico.dominio.modelo.Clasificacion;
import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;
import com.vivero.fitodiagnostico.dominio.modelo.ExposicionLuminica;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Periodo;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.modelo.ResultadoParametro;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** El mapa de ánimo (D136): parámetro y dirección a cara; desmayo, sin datos y cansancio. */
class MapaDeAnimoDeMascotaTest {

    private final MapaDeAnimo mapa = new MapaDeAnimo(ParametrosDeMascota.POR_DEFECTO);
    private final UUID planta = UUID.randomUUID();
    private final Rango rango = new Rango(30, 60);

    private ResultadoParametro optimo(Magnitud m) {
        return new ResultadoParametro(m, 45, rango, Clasificacion.OPTIMO);
    }

    private ResultadoParametro fuera(Magnitud m, Clasificacion c, double valor) {
        return new ResultadoParametro(m, valor, rango, c);
    }

    private Optional<Diagnostico> diagnostico(ResultadoParametro... parametros) {
        return Optional.of(new Diagnostico(null, null, EstadoGlobal.EN_RIESGO, List.of(parametros), List.of(), Instant.now()));
    }

    private Optional<EstadoDeMascota> conVitalidad(double v, boolean desmayada) {
        return Optional.of(new EstadoDeMascota(planta, v, desmayada, EstadoGlobal.SALUDABLE, Instant.now(), Instant.now(),
                null, 0, null, null, null));
    }

    @ParameterizedTest
    @CsvSource({
            "HUMEDAD, BAJO, 10, SEDIENTA",
            "HUMEDAD, ALTO, 80, AHOGADA",
            "TEMPERATURA, BAJO, 10, CON_FRIO",
            "TEMPERATURA, ALTO, 80, CON_CALOR",
            "LUZ, BAJO, 10, A_OSCURAS",
            "LUZ, ALTO, 80, DESLUMBRADA"})
    void cadaParametroYDireccionTieneSuCara(Magnitud magnitud, Clasificacion direccion, double valor, Animo esperado) {
        MapaDeAnimo.Resultado r = mapa.de(diagnostico(fuera(magnitud, direccion, valor)), conVitalidad(80, false));

        assertThat(r.animo()).isEqualTo(esperado);
        assertThat(r.causa()).hasValueSatisfying(c -> {
            assertThat(c.magnitud()).isEqualTo(magnitud);
            assertThat(c.direccion()).isEqualTo(direccion);
        });
    }

    @Test
    void siFallanVariosGanaElQueMasSeDesvia() {
        // humedad 25 (5 bajo el mínimo, 0,17 del ancho) contra temperatura 75 (15 sobre el máximo, 0,5).
        MapaDeAnimo.Resultado r = mapa.de(diagnostico(fuera(Magnitud.HUMEDAD, Clasificacion.BAJO, 25),
                fuera(Magnitud.TEMPERATURA, Clasificacion.ALTO, 75), optimo(Magnitud.LUZ)), conVitalidad(80, false));

        assertThat(r.animo()).isEqualTo(Animo.CON_CALOR);
        assertThat(r.causa().orElseThrow().desviacion()).isEqualTo(0.5);
    }

    @Test
    void todoEnRangoEsFelizOCansadaSiLaVitalidadSigueBaja() {
        Optional<Diagnostico> bien = diagnostico(optimo(Magnitud.HUMEDAD), optimo(Magnitud.LUZ), optimo(Magnitud.TEMPERATURA));

        assertThat(mapa.de(bien, conVitalidad(80, false)).animo()).isEqualTo(Animo.FELIZ);
        assertThat(mapa.de(bien, conVitalidad(20, false)).animo()).isEqualTo(Animo.CANSADA);
        assertThat(mapa.de(bien, conVitalidad(20, false)).causa()).isEmpty();
    }

    @Test
    void desmayadaGanaSobreTodoYSinDiagnosticoNoHayDatos() {
        assertThat(mapa.de(diagnostico(optimo(Magnitud.HUMEDAD)), conVitalidad(0, true)).animo()).isEqualTo(Animo.DESMAYADA);
        assertThat(mapa.de(Optional.empty(), Optional.empty()).animo()).isEqualTo(Animo.SIN_DATOS);
    }

    // ---- Fase 12 (D170): la mascota conoce la noche ----

    private Optional<Diagnostico> deNoche(ResultadoParametro... parametros) {
        return Optional.of(new Diagnostico(null, null, EstadoGlobal.SALUDABLE, List.of(parametros), List.of(),
                Instant.now(), Periodo.NOCHE, new ExposicionLuminica(24, 9, 800, 700, 3)));
    }

    @ParameterizedTest
    @CsvSource({"BAJO, 10", "ALTO, 80"})
    void deNocheSiLoUnicoQueFallaEsLaLuzDuerme(Clasificacion direccion, double valor) {
        MapaDeAnimo.Resultado r = mapa.de(deNoche(optimo(Magnitud.HUMEDAD), fuera(Magnitud.LUZ, direccion, valor),
                optimo(Magnitud.TEMPERATURA)), conVitalidad(80, false));

        assertThat(r.animo()).isEqualTo(Animo.DORMIDA);
        assertThat(r.causa()).isEmpty();
    }

    @Test
    void deNocheSiFallaOtraCosaGanaEsaAunqueLaLuzSeDesvieMas() {
        MapaDeAnimo.Resultado r = mapa.de(deNoche(fuera(Magnitud.HUMEDAD, Clasificacion.BAJO, 28),
                fuera(Magnitud.LUZ, Clasificacion.BAJO, -500)), conVitalidad(80, false));

        assertThat(r.animo()).isEqualTo(Animo.SEDIENTA);
        assertThat(r.causa()).hasValueSatisfying(c -> assertThat(c.magnitud()).isEqualTo(Magnitud.HUMEDAD));
    }

    @Test
    void deNocheConTodoEnRangoSigueSiendoFelizYDeDiaLaLuzBajaSigueSiendoAOscuras() {
        assertThat(mapa.de(deNoche(optimo(Magnitud.LUZ)), conVitalidad(80, false)).animo()).isEqualTo(Animo.FELIZ);
        assertThat(mapa.de(diagnostico(fuera(Magnitud.LUZ, Clasificacion.BAJO, 10)), conVitalidad(80, false)).animo())
                .isEqualTo(Animo.A_OSCURAS);
    }

    @Test
    void desmayadaDeNocheTampocoCulpaALaLuz() {
        MapaDeAnimo.Resultado r = mapa.de(deNoche(fuera(Magnitud.LUZ, Clasificacion.BAJO, 10)), conVitalidad(0, true));

        assertThat(r.animo()).isEqualTo(Animo.DESMAYADA);
        assertThat(r.causa()).isEmpty();
    }
}
