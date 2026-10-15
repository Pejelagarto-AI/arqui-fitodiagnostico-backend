package com.vivero.fitodiagnostico.infraestructura.notificacion;

import com.vivero.fitodiagnostico.dobles.NotificadorGrabador;
import com.vivero.fitodiagnostico.dominio.modelo.Aviso;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import com.vivero.fitodiagnostico.dominio.puerto.Notificador;
import com.vivero.fitodiagnostico.infraestructura.notificacion.NotificadorCompuesto.Canal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** La tabla de rutas (D58): qué tipo de aviso sale por qué canal, y que un canal caído no tumba a los demás. */
class NotificadorCompuestoTest {

    private final NotificadorGrabador registro = new NotificadorGrabador();
    private final NotificadorGrabador correo = new NotificadorGrabador();
    private final NotificadorGrabador vivo = new NotificadorGrabador();
    private NotificadorCompuesto compuesto;

    @BeforeEach
    void preparar() {
        compuesto = new NotificadorCompuesto(Map.of(Canal.REGISTRO, registro, Canal.CORREO, correo, Canal.VIVO, vivo));
    }

    private Aviso aviso(TipoDeAviso tipo) {
        return new Aviso(Set.of(UUID.randomUUID()), tipo, "hola");
    }

    @Test
    void cadaTipoDeAvisoTieneRutaDefinida() {
        // Si alguien agrega un tipo y olvida decidir su ruta, el aviso se perdería en silencio: aquí falla.
        for (TipoDeAviso tipo : TipoDeAviso.values()) {
            assertThat(NotificadorCompuesto.RUTAS).as("ruta de %s", tipo).containsKey(tipo);
            assertThat(NotificadorCompuesto.RUTAS.get(tipo)).as("canales de %s", tipo).isNotEmpty();
        }
    }

    @Test
    void elDiagnosticoNuevoSoloVaPorLaVistaEnVivo() {
        compuesto.avisar(aviso(TipoDeAviso.DIAGNOSTICO_NUEVO));

        assertThat(vivo.avisos()).hasSize(1);
        assertThat(registro.avisos()).isEmpty();
        assertThat(correo.avisos()).isEmpty();
    }

    @Test
    void cambioDeEstadoAccesoPerdidoYSolicitudResueltaVanPorLosTresCanales() {
        for (TipoDeAviso tipo : new TipoDeAviso[]{TipoDeAviso.CAMBIO_DE_ESTADO, TipoDeAviso.ACCESO_PERDIDO,
                TipoDeAviso.SOLICITUD_RESUELTA}) {
            compuesto.avisar(aviso(tipo));
        }

        assertThat(registro.avisos()).hasSize(3);
        assertThat(correo.avisos()).hasSize(3);
        assertThat(vivo.avisos()).hasSize(3);
    }

    @Test
    void invitacionYRestablecimientoNuncaVanPorLaVistaEnVivoPorqueLlevanUnToken() {
        compuesto.avisar(aviso(TipoDeAviso.INVITACION));
        compuesto.avisar(aviso(TipoDeAviso.RESTABLECIMIENTO));

        assertThat(correo.avisos()).hasSize(2);
        assertThat(registro.avisos()).hasSize(2);
        assertThat(vivo.avisos()).isEmpty();
    }

    @Test
    void entregaElMismoAvisoSinModificarlo() {
        Aviso original = aviso(TipoDeAviso.CAMBIO_DE_ESTADO);

        compuesto.avisar(original);

        assertThat(correo.avisos()).containsExactly(original);
        assertThat(vivo.avisos()).containsExactly(original);
    }

    @Test
    void siUnCanalFallaLosDemasSiguenRecibiendoYNoSePropagaLaExcepcion() {
        Notificador roto = aviso -> {
            throw new IllegalStateException("SMTP caído");
        };
        NotificadorCompuesto conUnoRoto = new NotificadorCompuesto(
                Map.of(Canal.REGISTRO, registro, Canal.CORREO, roto, Canal.VIVO, vivo));

        conUnoRoto.avisar(aviso(TipoDeAviso.CAMBIO_DE_ESTADO));

        assertThat(registro.avisos()).hasSize(1);
        assertThat(vivo.avisos()).hasSize(1);
    }

    @Test
    void unCanalSinAdaptadorSeOmiteSinFallar() {
        NotificadorCompuesto soloRegistro = new NotificadorCompuesto(Map.of(Canal.REGISTRO, registro));

        soloRegistro.avisar(aviso(TipoDeAviso.CAMBIO_DE_ESTADO));

        assertThat(registro.avisos()).hasSize(1);
    }
}
