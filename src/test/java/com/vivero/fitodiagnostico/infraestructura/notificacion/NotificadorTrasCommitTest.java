package com.vivero.fitodiagnostico.infraestructura.notificacion;

import com.vivero.fitodiagnostico.dominio.modelo.Aviso;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import com.vivero.fitodiagnostico.dominio.puerto.Notificador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * D57: el aviso sale DESPUÉS del commit, nunca en rollback. Con un gestor de
 * transacciones real (JDBC sobre H2 en memoria) y sin contexto de Spring: lo que se
 * prueba es el decorador, no el cableado.
 */
class NotificadorTrasCommitTest {

    private final List<String> bitacora = new ArrayList<>();
    private final List<Aviso> entregados = new ArrayList<>();
    private TransactionTemplate transaccion;
    private NotificadorTrasCommit notificador;

    @BeforeEach
    void preparar() {
        DriverManagerDataSource fuente = new DriverManagerDataSource("jdbc:h2:mem:tras-commit-" + UUID.randomUUID(), "sa", "");
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));
        notificador = new NotificadorTrasCommit(aviso -> {
            bitacora.add("entregado");
            entregados.add(aviso);
        });
    }

    private static Aviso aviso() {
        return new Aviso(Set.of(UUID.randomUUID()), TipoDeAviso.CAMBIO_DE_ESTADO, "cambió");
    }

    @Test
    void conTransaccionElAvisoSaleDespuesDelCommitNoAntes() {
        transaccion.executeWithoutResult(estado -> {
            notificador.avisar(aviso());
            bitacora.add("fin del método de negocio");
            assertThat(entregados).as("dentro de la transacción todavía no sale").isEmpty();
        });
        bitacora.add("commit hecho");

        assertThat(entregados).hasSize(1);
        assertThat(bitacora).containsExactly("fin del método de negocio", "entregado", "commit hecho");
    }

    @Test
    void siLaTransaccionSeRevierteElAvisoNoSaleNunca() {
        assertThatThrownBy(() -> transaccion.executeWithoutResult(estado -> {
            notificador.avisar(aviso());
            throw new IllegalStateException("falló algo después de avisar");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(entregados).isEmpty();
    }

    @Test
    void siLaTransaccionSeMarcaSoloParaRevertirTampocoSale() {
        transaccion.executeWithoutResult(estado -> {
            notificador.avisar(aviso());
            estado.setRollbackOnly();
        });

        assertThat(entregados).isEmpty();
    }

    @Test
    void variosAvisosDeUnaTransaccionSalenTodosYEnOrden() {
        Aviso uno = aviso();
        Aviso dos = aviso();

        transaccion.executeWithoutResult(estado -> {
            notificador.avisar(uno);
            notificador.avisar(dos);
        });

        assertThat(entregados).containsExactly(uno, dos);
    }

    @Test
    void sinTransaccionElAvisoSaleDeInmediato() {
        notificador.avisar(aviso());

        assertThat(entregados).hasSize(1);
    }

    @Test
    void unDestinoQueFallaNoRompeElCommitNiPropagaLaExcepcion() {
        Notificador roto = aviso -> {
            throw new IllegalStateException("el canal explotó");
        };
        NotificadorTrasCommit conDestinoRoto = new NotificadorTrasCommit(roto);

        // Si la excepción saliera de afterCommit, el llamador vería un error por algo que SÍ quedó guardado.
        transaccion.executeWithoutResult(estado -> conDestinoRoto.avisar(aviso()));
        conDestinoRoto.avisar(aviso());   // y sin transacción tampoco
    }
}
