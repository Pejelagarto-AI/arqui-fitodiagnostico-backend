package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.dobles.Muestras;
import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.OrigenDeMedicion;
import com.vivero.fitodiagnostico.dominio.modelo.Paginacion;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.SerieTemporal;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 12 (D177): cuenta las sentencias SQL (estadísticas de Hibernate) de las consultas más frecuentes y mide la
 * serie de luz con un día completo de lecturas cada 5 s. Imprime las cifras ("MEDICION ...") y fija los topes que
 * se cumplen después del pulido: si alguien vuelve a introducir un N+1, esta prueba falla.
 */
class ConsultasDelHistorialMedicionTest extends BaseDePersistenciaDePlantas {

    private static final int LECTURAS_POR_DIA = 17_280;   // una cada 5 s
    private Statistics estadisticas;

    @BeforeEach
    void contar() {
        estadisticas = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        estadisticas.setStatisticsEnabled(true);
    }

    private long sentencias(Runnable consulta) {
        sincronizar();
        estadisticas.clear();
        consulta.run();
        return estadisticas.getPrepareStatementCount();
    }

    @Test
    void listarLasPlantasDeUnDuenoNoHaceUnaConsultaPorEspecie() {
        Usuario dueno = usuario("lista");
        List<Especie> variadas = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            variadas.add(especie());
        }
        for (int i = 0; i < 15; i++) {
            planta(dueno, variadas.get(i % variadas.size()), "p" + i, ahora().plusSeconds(i));
        }
        long propias = sentencias(() -> assertThat(plantas.deDueno(dueno.id())).hasSize(15));

        Usuario invitado = usuario("invitado");
        plantas.deDueno(dueno.id()).forEach(p -> accesos.otorgar(new com.vivero.fitodiagnostico.dominio.modelo.AccesoCompartido(
                p.id(), invitado.id(), ahora())));
        sincronizar();
        long compartidas = sentencias(() -> assertThat(plantas.compartidasCon(invitado.id())).hasSize(15));

        System.out.println("MEDICION listado de 15 plantas con 5 especies: propias=" + propias + " compartidas=" + compartidas);
        assertThat(propias).isEqualTo(1);
        assertThat(compartidas).isEqualTo(1);
    }

    @Test
    void unaPaginaDelHistorialNoHaceConsultasPorFila() {
        Usuario dueno = usuario("historial");
        Especie especie = especie();
        Planta planta = planta(dueno, especie);
        Instant base = ahora().minus(Duration.ofHours(1));
        for (int i = 0; i < 100; i++) {
            Diagnostico d = evaluar(especie, Muestras.medicionCritica());
            diagnosticos.guardar(DiagnosticoDePlanta.deUnaEvaluacion(planta.id(), OrigenDeMedicion.DISPOSITIVO,
                    base.plusSeconds(5L * i), d));
        }
        long pagina = sentencias(() -> assertThat(diagnosticos.historialDe(planta.id(), new Paginacion(0, 100))
                .elementos()).hasSize(100));
        long ultimo = sentencias(() -> assertThat(diagnosticos.ultimoDe(planta.id())).isPresent());

        System.out.println("MEDICION historial de 100 diagnosticos: pagina=" + pagina + " ultimo=" + ultimo);
        assertThat(pagina).isLessThanOrEqualTo(4);   // la página, el conteo y una consulta por colección (en lote)
        assertThat(ultimo).isLessThanOrEqualTo(3);
    }

    @Test
    void laSerieDeLuzDeUnDiaNoRecorreLasDiecisieteMilLecturas() {
        Usuario dueno = usuario("serie");
        Especie especie = especie();
        Planta planta = planta(dueno, especie);
        Instant ahora = Instant.parse("2026-11-16T15:30:07Z");
        sembrarDia(planta, especie, ahora);

        // La primera lectura después de que cierra cada hora calcula (y guarda) el resumen de las que faltan; las demás no.
        sincronizar();
        estadisticas.clear();
        long inicioPrimera = System.nanoTime();
        diagnosticos.serieDe(planta.id(), Magnitud.LUZ, ahora.minus(Duration.ofHours(24)), ahora);
        long primeraMs = (System.nanoTime() - inicioPrimera) / 1_000_000;
        long primeraSentencias = estadisticas.getPrepareStatementCount();
        System.out.println("MEDICION primera serie tras sembrar el dia: " + primeraMs + " ms, " + primeraSentencias
                + " sentencias (calcula y guarda 23 resumenes)");

        // Se repite para quitar el ruido del arranque de la JVM; la cifra que cuenta es la última.
        long milis = 0;
        long sentencias = 0;
        SerieTemporal serie = null;
        for (int vez = 0; vez < 3; vez++) {
            sincronizar();
            estadisticas.clear();
            long inicio = System.nanoTime();
            serie = diagnosticos.serieDe(planta.id(), Magnitud.LUZ, ahora.minus(Duration.ofHours(24)), ahora);
            milis = (System.nanoTime() - inicio) / 1_000_000;
            sentencias = estadisticas.getPrepareStatementCount();
        }
        System.out.println("MEDICION serie de luz de 24 h (" + LECTURAS_POR_DIA + " lecturas guardadas): " + milis
                + " ms, " + sentencias + " sentencias, " + serie.muestras().size() + " muestras devueltas al dominio");
        assertThat(serie.estaVacia()).isFalse();
        assertThat(sentencias).isLessThanOrEqualTo(2);                       // los resúmenes y la hora en curso
        assertThat(serie.muestras().size()).isLessThan(LECTURAS_POR_DIA / 10);   // pocas, no 17 000
    }

    /** Un día de lecturas cada 5 s hasta {@code hasta}: noche oscura, y entre las 6 y las 18 (UTC) 800 lux. */
    private void sembrarDia(Planta planta, Especie especie, Instant hasta) {
        Instant inicio = hasta.minus(Duration.ofSeconds(5L * LECTURAS_POR_DIA)).plusSeconds(5);
        SiembraDeLecturas.sembrar(jdbc, planta.id(), especie.nombre(), inicio, hasta.plusSeconds(1), 5, t -> {
            int hora = t.atZone(java.time.ZoneOffset.UTC).getHour();
            return hora >= 6 && hora < 18 ? 800.0 : 0.0;
        });
        sincronizar();
    }
}
