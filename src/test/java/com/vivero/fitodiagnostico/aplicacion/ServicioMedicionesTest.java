package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dobles.Muestras;
import com.vivero.fitodiagnostico.dobles.MundoEnMemoria;
import com.vivero.fitodiagnostico.dobles.RelojManual;
import com.vivero.fitodiagnostico.dominio.excepcion.OperacionNoPermitidaException;
import com.vivero.fitodiagnostico.dominio.excepcion.PlantaNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.modelo.AccesoCompartido;
import com.vivero.fitodiagnostico.dominio.modelo.Aviso;
import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;
import com.vivero.fitodiagnostico.dominio.modelo.OrigenDeMedicion;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import com.vivero.fitodiagnostico.dominio.servicio.ClasificadorDeParametros;
import com.vivero.fitodiagnostico.dominio.servicio.DetectorDeCambioDeEstado;
import com.vivero.fitodiagnostico.dominio.servicio.EvaluadorDeEstado;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAcceso;
import com.vivero.fitodiagnostico.dominio.servicio.RedactorDeRecomendaciones;
import com.vivero.fitodiagnostico.dominio.servicio.ReglaPorDesviacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ServicioMediciones} con dobles en memoria y el dominio REAL (evaluador
 * y detector de verdad): lo que se prueba es la orquestación, no que el
 * evaluador clasifique bien, que ya tiene sus pruebas.
 */
class ServicioMedicionesTest {

    private final UUID dueno = UUID.randomUUID();
    private final UUID invitado = UUID.randomUUID();
    private final UUID ajeno = UUID.randomUUID();

    private final RelojManual reloj = RelojManual.enUnDiaCualquiera();

    private MundoEnMemoria mundo;
    private ServicioMediciones servicio;
    private Planta planta;

    @BeforeEach
    void preparar() {
        mundo = new MundoEnMemoria();
        servicio = new ServicioMediciones(mundo.plantas, mundo.accesos, mundo.diagnosticos,
                new EvaluadorDeEstado(new ClasificadorDeParametros(),
                        new ReglaPorDesviacion(ReglaPorDesviacion.UMBRAL_POR_DEFECTO), new RedactorDeRecomendaciones()),
                new DetectorDeCambioDeEstado(), mundo.notificador, new PoliticaDeAcceso(), reloj);
        planta = Planta.nueva(dueno, "Pepa", Muestras.especie("helecho"));
        mundo.plantas.guardar(planta);
        mundo.accesos.otorgar(AccesoCompartido.otorgadoAhora(planta.id(), invitado));
    }

    @Test
    void registrarManualEvaluaConLaEspecieDeLaPlantaYGuardaEnElHistorialConOrigenManual() {
        DiagnosticoDePlanta registro = servicio.registrarManual(dueno, planta.id(), Muestras.medicionEnRiesgo());

        assertThat(registro.plantaId()).isEqualTo(planta.id());
        assertThat(registro.origen()).isEqualTo(OrigenDeMedicion.MANUAL);
        assertThat(registro.estado()).isEqualTo(EstadoGlobal.EN_RIESGO);
        assertThat(registro.diagnostico().especie().nombre()).isEqualTo("helecho");
        assertThat(registro.diagnostico().recomendaciones()).isNotEmpty();
        assertThat(mundo.diagnosticos.ultimoDe(planta.id())).contains(registro);
    }

    @Test
    void elCasoDeUsoRecibeElOrigenComoParametroYGuardaElDeDispositivoSinTocarNadaMas() {
        // D17: la maceta será "otro adaptador de entrada": mismo caso de uso, otro origen.
        DiagnosticoDePlanta registro = servicio.registrar(planta.id(), Muestras.medicionSaludable(),
                OrigenDeMedicion.DISPOSITIVO, reloj.instant());

        assertThat(registro.origen()).isEqualTo(OrigenDeMedicion.DISPOSITIVO);
        assertThat(mundo.diagnosticos.ultimoDe(planta.id()).orElseThrow().origen()).isEqualTo(OrigenDeMedicion.DISPOSITIVO);
    }

    @Test
    void laEntradaManualUsaElRelojYElDispositivoConservaLaMarcaDeTiempoQueInforma() {
        DiagnosticoDePlanta manual = servicio.registrarManual(dueno, planta.id(), Muestras.medicionSaludable());
        assertThat(manual.tomadaEn()).isEqualTo(reloj.instant());

        Instant tomada = reloj.instant().minusSeconds(3600);
        DiagnosticoDePlanta delDispositivo = servicio.registrar(planta.id(), Muestras.medicionSaludable(),
                OrigenDeMedicion.DISPOSITIVO, tomada);

        assertThat(delDispositivo.tomadaEn()).isEqualTo(tomada);
        assertThat(delDispositivo.registradoEn()).isNotEqualTo(tomada);
    }

    @Test
    void registrarSobreUnaPlantaInexistenteLanza() {
        assertThatThrownBy(() -> servicio.registrar(UUID.randomUUID(), Muestras.medicionSaludable(),
                OrigenDeMedicion.DISPOSITIVO, reloj.instant())).isInstanceOf(PlantaNoEncontradaException.class);
        assertThat(mundo.diagnosticos.escrituras()).isZero();
    }

    @Test
    void elInvitadoNoRegistraMedicionesYNoQuedaNadaGuardadoNiAvisado() {
        // RN5.
        assertThatThrownBy(() -> servicio.registrarManual(invitado, planta.id(), Muestras.medicionSaludable()))
                .isInstanceOf(OperacionNoPermitidaException.class);

        assertThat(mundo.diagnosticos.escrituras()).isZero();
        assertThat(mundo.notificador.avisos()).isEmpty();
    }

    @Test
    void unAjenoRecibeLaMismaExcepcionQueSiLaPlantaNoExistiera() {
        assertThatThrownBy(() -> servicio.registrarManual(ajeno, planta.id(), Muestras.medicionSaludable()))
                .isInstanceOf(PlantaNoEncontradaException.class);
        assertThat(mundo.diagnosticos.escrituras()).isZero();
    }

    // ---- RF-G1, RN8 ----

    @Test
    void elPrimerDiagnosticoNoAvisaNadaAunqueNazcaCritico() {
        servicio.registrarManual(dueno, planta.id(), Muestras.medicionCritica());

        assertThat(mundo.notificador.avisosDeTipo(TipoDeAviso.CAMBIO_DE_ESTADO)).isEmpty();
    }

    @Test
    void unCambioDeEstadoAvisaUnaVezAlDuenoYALosInvitados() {
        servicio.registrarManual(dueno, planta.id(), Muestras.medicionSaludable());

        servicio.registrarManual(dueno, planta.id(), Muestras.medicionCritica());

        assertThat(mundo.notificador.avisosDeTipo(TipoDeAviso.CAMBIO_DE_ESTADO)).singleElement().satisfies((Aviso aviso) -> {
            assertThat(aviso.tipo()).isEqualTo(TipoDeAviso.CAMBIO_DE_ESTADO);
            assertThat(aviso.destinatarios()).containsExactlyInAnyOrder(dueno, invitado);
            assertThat(aviso.mensaje()).contains("Pepa").contains("SALUDABLE").contains("CRITICO");
        });
    }

    @Test
    void sinInvitadosElUnicoDestinatarioEsElDueno() {
        mundo.accesos.revocarTodosDe(planta.id());
        servicio.registrarManual(dueno, planta.id(), Muestras.medicionSaludable());

        servicio.registrarManual(dueno, planta.id(), Muestras.medicionEnRiesgo());

        assertThat(mundo.notificador.avisosDeTipo(TipoDeAviso.CAMBIO_DE_ESTADO).get(0).destinatarios())
                .isEqualTo(Set.of(dueno));
    }

    @Test
    void repetirElMismoEstadoNoAvisa() {
        servicio.registrarManual(dueno, planta.id(), Muestras.medicionEnRiesgo());
        servicio.registrarManual(dueno, planta.id(), Muestras.medicionEnRiesgo());
        servicio.registrarManual(dueno, planta.id(), Muestras.medicion(25, 700, 22));   // otro valor, mismo estado

        assertThat(mundo.diagnosticos.cuantos()).isEqualTo(3);
        assertThat(mundo.notificador.avisosDeTipo(TipoDeAviso.CAMBIO_DE_ESTADO)).isEmpty();
    }

    @Test
    void laComparacionEsContraElInmediatamenteAnteriorDeEsaPlantaNoContraOtra() {
        // RN8: otra planta crítica no hace que esta "cambie".
        Planta otra = Planta.nueva(dueno, "Otra", Muestras.especie("helecho"));
        mundo.plantas.guardar(otra);
        servicio.registrarManual(dueno, otra.id(), Muestras.medicionCritica());

        servicio.registrarManual(dueno, planta.id(), Muestras.medicionSaludable());   // primera de ESTA planta
        servicio.registrarManual(dueno, planta.id(), Muestras.medicionSaludable());
        assertThat(mundo.notificador.avisosDeTipo(TipoDeAviso.CAMBIO_DE_ESTADO)).isEmpty();

        servicio.registrarManual(dueno, planta.id(), Muestras.medicionCritica());     // SALUDABLE -> CRITICO
        servicio.registrarManual(dueno, planta.id(), Muestras.medicionSaludable());   // CRITICO -> SALUDABLE
        assertThat(mundo.notificador.avisosDeTipo(TipoDeAviso.CAMBIO_DE_ESTADO)).hasSize(2);
    }

    // ---- RF-G2: DIAGNOSTICO_NUEVO en cada medición ----

    @Test
    void cadaMedicionEmiteUnDiagnosticoNuevoAlDuenoYALosInvitadosAunqueElEstadoNoCambie() {
        servicio.registrarManual(dueno, planta.id(), Muestras.medicionEnRiesgo());   // primera: sin cambio de estado
        servicio.registrarManual(dueno, planta.id(), Muestras.medicionEnRiesgo());   // mismo estado

        assertThat(mundo.notificador.avisosDeTipo(TipoDeAviso.DIAGNOSTICO_NUEVO)).hasSize(2)
                .allSatisfy(aviso -> {
                    assertThat(aviso.destinatarios()).containsExactlyInAnyOrder(dueno, invitado);
                    assertThat(aviso.datos()).containsEntry(Aviso.PLANTA_ID, planta.id().toString())
                            .containsEntry(Aviso.ESTADO, "EN_RIESGO")
                            .containsEntry(Aviso.ORIGEN, "MANUAL").containsKey(Aviso.REGISTRADO_EN);
                });
        assertThat(mundo.notificador.avisosDeTipo(TipoDeAviso.CAMBIO_DE_ESTADO)).isEmpty();
    }

    @Test
    void unCambioDeEstadoEmiteLosDosAvisosYAmbosLlevanLaPlanta() {
        servicio.registrarManual(dueno, planta.id(), Muestras.medicionSaludable());
        servicio.registrarManual(dueno, planta.id(), Muestras.medicionCritica());

        assertThat(mundo.notificador.avisosDeTipo(TipoDeAviso.DIAGNOSTICO_NUEVO)).hasSize(2);
        assertThat(mundo.notificador.avisosDeTipo(TipoDeAviso.CAMBIO_DE_ESTADO)).singleElement()
                .satisfies(aviso -> assertThat(aviso.datos()).containsEntry(Aviso.PLANTA_ID, planta.id().toString()));
    }

    @Test
    void unaMedicionRechazadaNoEmiteNingunAviso() {
        assertThatThrownBy(() -> servicio.registrarManual(invitado, planta.id(), Muestras.medicionSaludable()))
                .isInstanceOf(OperacionNoPermitidaException.class);

        assertThat(mundo.notificador.avisos()).isEmpty();
    }

    @Test
    void unaEspecieDescontinuadaSigueDiagnosticandose() {
        // RN11.
        Planta vieja = new Planta(UUID.randomUUID(), dueno, "Vieja", Muestras.especieInactiva("potos"),
                planta.registradaEn());
        mundo.plantas.guardar(vieja);

        DiagnosticoDePlanta registro = servicio.registrarManual(dueno, vieja.id(), Muestras.medicionSaludable());

        assertThat(registro.estado()).isEqualTo(EstadoGlobal.SALUDABLE);
    }

    @Test
    void unaMedicionNoRegistraNadaSiElDominioLaRechazaAntesDeLlegar() {
        // La lectura imposible se rechaza al construir la Medicion, en el borde: este servicio nunca la ve.
        assertThatThrownBy(() -> Muestras.medicion(-5, 700, 22))
                .isInstanceOf(com.vivero.fitodiagnostico.dominio.excepcion.LecturaInvalidaException.class);
        assertThat(mundo.diagnosticos.escrituras()).isZero();
    }
}
