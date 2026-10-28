package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dobles.EmisorTokenDispositivoFijo;
import com.vivero.fitodiagnostico.dobles.Muestras;
import com.vivero.fitodiagnostico.dobles.MundoEnMemoria;
import com.vivero.fitodiagnostico.dominio.excepcion.OperacionNoPermitidaException;
import com.vivero.fitodiagnostico.dominio.excepcion.PlantaNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.modelo.AccesoCompartido;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.TokenDeDispositivo;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAcceso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Vincular un dispositivo: la regla "solo el dueño" con dobles en memoria, sin JWT ni Spring. */
class ServicioDispositivosTest {

    private final UUID dueno = UUID.randomUUID();
    private final UUID invitado = UUID.randomUUID();
    private final UUID ajeno = UUID.randomUUID();

    private MundoEnMemoria mundo;
    private EmisorTokenDispositivoFijo emisor;
    private ServicioDispositivos servicio;
    private Planta planta;

    @BeforeEach
    void preparar() {
        mundo = new MundoEnMemoria();
        emisor = new EmisorTokenDispositivoFijo();
        servicio = new ServicioDispositivos(mundo.plantas, mundo.accesos, new PoliticaDeAcceso(), emisor);
        planta = Planta.nueva(dueno, "Pepa", Muestras.especie("helecho"));
        mundo.plantas.guardar(planta);
        mundo.accesos.otorgar(AccesoCompartido.otorgadoAhora(planta.id(), invitado));
    }

    @Test
    void elDuenoRecibeUnTokenParaEseDispositivoYESaPlanta() {
        TokenDeDispositivo token = servicio.vincular(dueno, planta.id(), "esp32-01");

        assertThat(token.dispositivoId()).isEqualTo("esp32-01");
        assertThat(token.plantaId()).isEqualTo(planta.id());
        assertThat(token.valor()).isNotBlank();
        assertThat(emisor.emitidos()).containsExactly(token);
    }

    @Test
    void elInvitadoNoPuedeVincularYNoSeEmiteNada() {
        assertThatThrownBy(() -> servicio.vincular(invitado, planta.id(), "esp32-01"))
                .isInstanceOf(OperacionNoPermitidaException.class);
        assertThat(emisor.emitidos()).isEmpty();
    }

    @Test
    void unAjenoRecibeLaMismaExcepcionQueSiLaPlantaNoExistiera() {
        assertThatThrownBy(() -> servicio.vincular(ajeno, planta.id(), "esp32-01"))
                .isInstanceOf(PlantaNoEncontradaException.class);
        assertThatThrownBy(() -> servicio.vincular(dueno, UUID.randomUUID(), "esp32-01"))
                .isInstanceOf(PlantaNoEncontradaException.class);
        assertThat(emisor.emitidos()).isEmpty();
    }

    @Test
    void unIdentificadorMalFormadoSeRechazaSinEmitir() {
        assertThatThrownBy(() -> servicio.vincular(dueno, planta.id(), "con espacios/raros"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
