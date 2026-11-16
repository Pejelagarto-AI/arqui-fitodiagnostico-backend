package com.vivero.fitodiagnostico.dominio.puerto.salida;

import com.vivero.fitodiagnostico.dobles.DiagnosticosEnMemoria;
import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;
import com.vivero.fitodiagnostico.dominio.modelo.Lectura;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import com.vivero.fitodiagnostico.dominio.modelo.Muestra;
import com.vivero.fitodiagnostico.dominio.modelo.OrigenDeMedicion;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.modelo.SerieTemporal;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** La implementación por defecto de {@code RepositorioDiagnosticos.serieDe} (D127), sobre el doble en memoria. */
class SerieDelHistorialTest {

    private static final Instant T0 = Instant.parse("2026-11-16T00:00:00Z");
    private final DiagnosticosEnMemoria repositorio = new DiagnosticosEnMemoria();
    private final Especie especie = new Especie("suculenta", Map.of(Magnitud.LUZ, new Rango(800, 2500)));

    private void guardar(UUID plantaId, int minuto, double lux) {
        Instant instante = T0.plusSeconds(60L * minuto);
        Diagnostico diagnostico = new Diagnostico(especie, Medicion.de(new Lectura(Magnitud.LUZ, lux)),
                EstadoGlobal.SALUDABLE, List.of(), List.of(), instante);
        repositorio.guardar(new DiagnosticoDePlanta(UUID.randomUUID(), plantaId, OrigenDeMedicion.DISPOSITIVO,
                instante, instante, diagnostico));
    }

    @Test
    void recorreTodasLasPaginasNecesariasYRecortaLaVentana() {
        UUID planta = UUID.randomUUID();
        for (int minuto = 0; minuto < 250; minuto++) {
            guardar(planta, minuto, minuto);
        }
        guardar(UUID.randomUUID(), 100, 99_999);   // otra planta: no se mezcla

        SerieTemporal serie = repositorio.serieDe(planta, Magnitud.LUZ,
                T0.plusSeconds(60L * 10), T0.plusSeconds(60L * 240));

        assertThat(serie.muestras()).hasSize(230);
        assertThat(serie.muestras().get(0)).isEqualTo(new Muestra(T0.plusSeconds(600), 10));
        assertThat(serie.muestras().get(229).valor()).isEqualTo(239);
    }

    @Test
    void unaPlantaSinHistorialDaUnaSerieVacia() {
        assertThat(repositorio.serieDe(UUID.randomUUID(), Magnitud.LUZ, T0, T0.plusSeconds(60)).estaVacia()).isTrue();
    }
}
