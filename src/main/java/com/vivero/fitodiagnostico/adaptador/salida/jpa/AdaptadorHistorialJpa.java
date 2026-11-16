package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.historial.AlmacenDeHistorial;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adaptador JPA de la retención (D176): resume y borra en la misma transacción. */
@Repository
public class AdaptadorHistorialJpa implements AlmacenDeHistorial {

    private final DiagnosticoDePlantaJpaRepository diagnosticos;
    private final ResumenesDeLuz resumenes;

    public AdaptadorHistorialJpa(DiagnosticoDePlantaJpaRepository diagnosticos, ResumenesDeLuz resumenes) {
        this.diagnosticos = diagnosticos;
        this.resumenes = resumenes;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> plantasConLecturasAnterioresA(Instant corte) {
        return diagnosticos.plantasConLecturasAnterioresA(corte);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> primeraLecturaDe(UUID plantaId) {
        return Optional.ofNullable(diagnosticos.primeraLecturaDe(plantaId));
    }

    @Override
    @Transactional
    public int resumirYBorrar(UUID plantaId, Instant desde, Instant hasta) {
        // La hora siguiente también: su primer tramo cuenta desde la última lectura de la hora que se va a borrar.
        resumenes.deLasHoras(plantaId, desde, hasta.plus(ResumenDeLuzPorHora.UNA_HORA));
        return diagnosticos.borrarAnterioresA(plantaId, desde, hasta);
    }
}
