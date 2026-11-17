package com.vivero.fitodiagnostico.mascota.aplicacion;

import com.vivero.fitodiagnostico.dobles.MascotaEnMemoria;
import com.vivero.fitodiagnostico.dobles.Muestras;
import com.vivero.fitodiagnostico.dobles.MundoEnMemoria;
import com.vivero.fitodiagnostico.dobles.RelojManual;
import com.vivero.fitodiagnostico.dominio.excepcion.OperacionNoPermitidaException;
import com.vivero.fitodiagnostico.dominio.excepcion.PlantaNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.modelo.AccesoCompartido;
import com.vivero.fitodiagnostico.dominio.modelo.Dispositivo;
import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAcceso;
import com.vivero.fitodiagnostico.mascota.modelo.Accesorio;
import com.vivero.fitodiagnostico.mascota.modelo.Animo;
import com.vivero.fitodiagnostico.mascota.modelo.EventoDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.ParametrosDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.ResultadoDeAccion;
import com.vivero.fitodiagnostico.mascota.modelo.TipoDeAccion;
import com.vivero.fitodiagnostico.mascota.modelo.TipoDeEvento;
import com.vivero.fitodiagnostico.mascota.modelo.VistaParaLaMatera;
import com.vivero.fitodiagnostico.mascota.modelo.EstadoDeMascota;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fase 13 con dobles en memoria: lo que la matera lee de su mascota (eventos desde su lectura anterior), la caricia
 * que viene de la matera con su origen, el accesorio y el resumen por lote del tablero.
 */
class ServicioMascotaDeMateraTest {

    private static final String SERIAL = "fito-MASCOTA";

    private final UUID dueno = UUID.randomUUID();
    private final UUID invitado = UUID.randomUUID();
    private final UUID ajeno = UUID.randomUUID();
    private final RelojManual reloj = RelojManual.enUnDiaCualquiera();

    private MundoEnMemoria mundo;
    private MascotaEnMemoria mascota;
    private ServicioMascota servicio;
    private ServicioVistasDeMascota vistas;
    private Planta planta;

    @BeforeEach
    void preparar() {
        mundo = new MundoEnMemoria();
        mascota = new MascotaEnMemoria(reloj);
        servicio = new ServicioMascota(mundo.plantas, mundo.accesos, mundo.diagnosticos, mundo.dispositivos,
                new PoliticaDeAcceso(), mascota, mascota, mascota, mascota, ParametrosDeMascota.POR_DEFECTO, reloj);
        vistas = new ServicioVistasDeMascota(mascota, mascota, mundo.diagnosticos,
                ids -> {
                    Map<UUID, com.vivero.fitodiagnostico.dominio.modelo.Diagnostico> r = new java.util.HashMap<>();
                    ids.forEach(id -> mundo.diagnosticos.ultimoDe(id)
                            .ifPresent(d -> r.put(id, d.diagnostico())));
                    return r;
                }, ParametrosDeMascota.POR_DEFECTO, reloj);
        planta = Planta.nueva(dueno, "Pepa", Muestras.especie("helecho"));
        mundo.plantas.guardar(planta);
        mundo.accesos.otorgar(AccesoCompartido.otorgadoAhora(planta.id(), invitado));
        Dispositivo matera = Dispositivo.deFabrica(SERIAL, "secreto-de-prueba", reloj.instant());
        matera.reclamar(planta.id(), reloj.instant());
        mundo.dispositivos.guardar(matera);
    }

    /** Una caricia de la persona, y los segundos que pide el enfriamiento (10 s) más uno. */
    private void caricia() {
        servicio.actuar(dueno, planta.id(), TipoDeAccion.ACARICIAR);
        reloj.avanzar(Duration.ofSeconds(11));
    }

    // ---- A: eventos desde la lectura anterior ----

    @Test
    void sinFilaDeMascotaLaVistaEsLaInicialSinEventos() {
        VistaParaLaMatera v = vistas.paraLaMatera(planta.id(), null);

        assertThat(v.resumen().vitalidad()).isEqualTo((int) Math.round(ParametrosDeMascota.VITALIDAD_INICIAL));
        assertThat(v.resumen().animo()).isEqualTo(Animo.SIN_DATOS);
        assertThat(v.resumen().racha()).isZero();
        assertThat(v.resumen().accesorio()).isEqualTo(Accesorio.NINGUNO);
        assertThat(v.eventos()).isEmpty();
    }

    @Test
    void traeSoloLosEventosRegistradosDespuesDeLaLecturaAnterior() {
        caricia();
        Instant anterior = reloj.instant().minusSeconds(5);   // entre la primera y la segunda
        caricia();
        caricia();

        VistaParaLaMatera v = vistas.paraLaMatera(planta.id(), anterior);

        assertThat(v.eventos()).hasSize(2);
        assertThat(v.eventos()).extracting(EventoDeMascota::tipo).containsOnly(TipoDeEvento.ACARICIADA);
    }

    @Test
    void enLaFronteraUnEventoRegistradoJustoEnLaLecturaAnteriorNoVuelveASalir() {
        servicio.actuar(dueno, planta.id(), TipoDeAccion.ACARICIAR);

        assertThat(vistas.paraLaMatera(planta.id(), reloj.instant()).eventos()).isEmpty();
        assertThat(vistas.paraLaMatera(planta.id(), reloj.instant().minusMillis(1)).eventos()).hasSize(1);
    }

    @Test
    void maximoDiezDelMasAntiguoAlMasRecienteQuedandoseConLosUltimos() {
        Instant antes = reloj.instant().minusSeconds(1);
        for (int i = 0; i < 12; i++) {
            caricia();
        }

        List<EventoDeMascota> eventos = vistas.paraLaMatera(planta.id(), antes).eventos();

        assertThat(eventos).hasSize(ServicioVistasDeMascota.EVENTOS_PARA_LA_MATERA);
        assertThat(eventos).extracting(EventoDeMascota::ocurridoEn).isSorted();
        // las dos primeras caricias (las más viejas) se quedaron fuera
        assertThat(eventos.get(0).ocurridoEn()).isEqualTo(antes.plusSeconds(1).plusSeconds(2 * 11));
    }

    @Test
    void sinLecturaAnteriorMiraLosUltimosSesentaSegundos() {
        caricia();                                  // t0
        reloj.avanzar(Duration.ofSeconds(120));
        caricia();                                  // hace unos 11 s

        VistaParaLaMatera v = vistas.paraLaMatera(planta.id(), null);

        assertThat(v.eventos()).hasSize(1);
    }

    // ---- B: la matera acaricia ----

    @Test
    void laCariciaDeLaMateraGuardaOrigenYNoSeLeAvisaAEsaMatera() {
        ResultadoDeAccion r = servicio.acariciar(planta.id(), SERIAL);

        assertThat(r.repetida()).isFalse();
        assertThat(mascota.publicados).hasSize(1);
        var aviso = mascota.publicados.get(0);
        assertThat(aviso.evento().tipo()).isEqualTo(TipoDeEvento.ACARICIADA);
        assertThat(aviso.evento().detalle()).containsEntry("origen", "MATERA");
        assertThat(aviso.seriales()).doesNotContain(SERIAL);
        assertThat(aviso.destinatarios()).containsExactlyInAnyOrder(dueno, invitado);
    }

    @Test
    void laCariciaDeUnaPersonaLlevaOrigenPersonaYSiLlegaALaMatera() {
        servicio.actuar(dueno, planta.id(), TipoDeAccion.ACARICIAR);

        var aviso = mascota.publicados.get(0);
        assertThat(aviso.evento().detalle()).containsEntry("origen", "PERSONA");
        assertThat(aviso.seriales()).containsExactly(SERIAL);
    }

    @Test
    void elEnfriamientoEsDeLaPlantaAsiQueUnaCariciaDeLaMateraTrasLaDeLaPersonaEsRepetida() {
        ResultadoDeAccion primera = servicio.actuar(dueno, planta.id(), TipoDeAccion.ACARICIAR);
        reloj.avanzar(Duration.ofSeconds(3));

        ResultadoDeAccion desdeLaMatera = servicio.acariciar(planta.id(), SERIAL);

        assertThat(desdeLaMatera.repetida()).isTrue();
        assertThat(desdeLaMatera.id()).isEqualTo(primera.id());
        assertThat(mascota.tiposDe(planta.id())).containsExactly(TipoDeEvento.ACARICIADA);
    }

    @Test
    void acariciarUnaPlantaQueYaNoExisteEs404() {
        assertThatThrownBy(() -> servicio.acariciar(UUID.randomUUID(), SERIAL))
                .isInstanceOf(PlantaNoEncontradaException.class);
    }

    // ---- C: accesorio ----

    @Test
    void elAccesorioCreaLaFilaConLaVitalidadInicialYAvisaSinHacerEvento() {
        servicio.cambiar(dueno, planta.id(), Accesorio.SOMBRERO);

        EstadoDeMascota fila = mascota.de(planta.id()).orElseThrow();
        assertThat(fila.accesorio()).isEqualTo(Accesorio.SOMBRERO);
        assertThat(fila.vitalidad()).isEqualTo(ParametrosDeMascota.VITALIDAD_INICIAL);
        assertThat(mascota.tiposDe(planta.id())).isEmpty();
        assertThat(mascota.publicados).isEmpty();
        assertThat(mascota.actualizaciones).hasSize(1);
        assertThat(mascota.actualizaciones.get(0).accesorio()).isEqualTo(Accesorio.SOMBRERO);
        assertThat(mascota.actualizaciones.get(0).destinatarios()).containsExactlyInAnyOrder(dueno, invitado);
    }

    @Test
    void ponerElMismoAccesorioNoVuelveAAvisar() {
        servicio.cambiar(dueno, planta.id(), Accesorio.GAFAS);
        servicio.cambiar(dueno, planta.id(), Accesorio.GAFAS);

        assertThat(mascota.actualizaciones).hasSize(1);
    }

    @Test
    void soloElDuenoCambiaElAccesorio() {
        assertThatThrownBy(() -> servicio.cambiar(invitado, planta.id(), Accesorio.MONO))
                .isInstanceOf(OperacionNoPermitidaException.class);
        assertThatThrownBy(() -> servicio.cambiar(ajeno, planta.id(), Accesorio.MONO))
                .isInstanceOf(PlantaNoEncontradaException.class);
        assertThat(mascota.de(planta.id())).isEmpty();
    }

    @Test
    void unaLecturaNuevaNoBorraElAccesorio() {
        servicio.cambiar(dueno, planta.id(), Accesorio.MONO);
        reloj.avanzar(Duration.ofMinutes(1));
        DiagnosticoDePlanta registro = DiagnosticoDePlanta.deUnaEvaluacion(planta.id(),
                com.vivero.fitodiagnostico.dominio.modelo.OrigenDeMedicion.DISPOSITIVO, reloj.instant(),
                Muestras.diagnosticoCon(com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal.SALUDABLE));
        mundo.diagnosticos.guardar(registro);

        servicio.alRegistrarLectura(registro);

        assertThat(mascota.de(planta.id()).orElseThrow().accesorio()).isEqualTo(Accesorio.MONO);
        assertThat(vistas.paraLaMatera(planta.id(), null).resumen().accesorio()).isEqualTo(Accesorio.MONO);
    }

    // ---- D: resumen por lote ----

    @Test
    void elResumenPorLoteSoloTraeLasPlantasConMascota() {
        Planta sinMascota = Planta.nueva(dueno, "Sola", Muestras.especie("helecho"));
        mundo.plantas.guardar(sinMascota);
        servicio.cambiar(dueno, planta.id(), Accesorio.GAFAS);

        var resumenes = vistas.deLasPlantas(List.of(planta.id(), sinMascota.id()));

        assertThat(resumenes).containsOnlyKeys(planta.id());
        assertThat(resumenes.get(planta.id()).accesorio()).isEqualTo(Accesorio.GAFAS);
        assertThat(resumenes.get(planta.id()).animo()).isEqualTo(Animo.SIN_DATOS);
        assertThat(vistas.deLasPlantas(List.of())).isEmpty();
        assertThat(Optional.ofNullable(resumenes.get(sinMascota.id()))).isEmpty();
    }
}
