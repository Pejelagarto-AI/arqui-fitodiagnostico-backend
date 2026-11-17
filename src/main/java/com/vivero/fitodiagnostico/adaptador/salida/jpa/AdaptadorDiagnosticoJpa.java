package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.mascota.puerto.salida.UltimosDiagnosticosEnLote;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Muestra;
import com.vivero.fitodiagnostico.dominio.modelo.Pagina;
import com.vivero.fitodiagnostico.dominio.modelo.Paginacion;
import com.vivero.fitodiagnostico.dominio.modelo.SerieTemporal;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioDiagnosticos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JPA del puerto {@link RepositorioDiagnosticos}. {@code Pageable} de
 * Spring Data vive solo aquí: el dominio habla de {@link Paginacion}.
 */
@Repository
public class AdaptadorDiagnosticoJpa implements RepositorioDiagnosticos, UltimosDiagnosticosEnLote {

    private final DiagnosticoDePlantaJpaRepository jpa;
    private final MapeadorDiagnosticoDePlanta mapeador;
    private final ResumenesDeLuz resumenes;

    public AdaptadorDiagnosticoJpa(DiagnosticoDePlantaJpaRepository jpa, MapeadorDiagnosticoDePlanta mapeador,
                                   ResumenesDeLuz resumenes) {
        this.jpa = jpa;
        this.mapeador = mapeador;
        this.resumenes = resumenes;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DiagnosticoDePlanta> ultimoDe(UUID plantaId) {
        return jpa.findFirstByPlantaIdOrderByRegistradoEnDescIdDesc(plantaId).map(mapeador::aDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Diagnostico> ultimosDe(Collection<UUID> plantaIds) {
        Map<UUID, Diagnostico> porPlanta = new HashMap<>();
        if (plantaIds.isEmpty()) {
            return porPlanta;
        }
        // Si dos diagnósticos de una planta comparten registrado_en, gana el de id mayor, como en ultimoDe.
        jpa.ultimosDe(plantaIds).stream()
                .sorted(java.util.Comparator.comparing(DiagnosticoDePlantaEntity::getId))
                .forEach(e -> porPlanta.put(e.getPlantaId(), mapeador.aDominio(e).diagnostico()));
        return porPlanta;
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<DiagnosticoDePlanta> historialDe(UUID plantaId, Paginacion paginacion) {
        Page<DiagnosticoDePlantaEntity> pagina = jpa.findByPlantaIdOrderByRegistradoEnDescIdDesc(
                plantaId, PageRequest.of(paginacion.pagina(), paginacion.tamano()));
        return new Pagina<>(pagina.getContent().stream().map(mapeador::aDominio).toList(),
                paginacion.pagina(), paginacion.tamano(), pagina.getTotalElements());
    }

    /**
     * Reemplaza la implementación por defecto del puerto (que pagina diagnósticos completos) por consultas (D127) y,
     * desde la fase 12, por el resumen por hora (D175): las horas enteras cerradas dentro de {@code [desde, hasta)} salen
     * del resumen (una fila por hora) y solo la hora en curso sale de las lecturas crudas. El puerto y el dominio no
     * cambian: el dominio recibe la serie de siempre, con pocas muestras sintéticas en lugar de las miles de la hora.
     *
     * <p>Aproximación (D175): la hora parcial más antigua de la ventana ({@code desde} hasta la hora en punto) no
     * cuenta, así que de 24 h se miran entre 23 y 24 h. Si el rango no contiene una hora entera cerrada, todo sale de
     * las crudas, como antes.
     */
    @Override
    @Transactional
    public SerieTemporal serieDe(UUID plantaId, Magnitud magnitud, Instant desde, Instant hasta) {
        Instant primeraCerrada = ResumenesDeLuz.primeraHoraDesde(desde);
        Instant horaEnCurso = ResumenesDeLuz.inicioDeLaHora(hasta);
        if (magnitud != Magnitud.LUZ || !primeraCerrada.isBefore(horaEnCurso)) {
            return crudas(plantaId, magnitud, desde, hasta);
        }
        List<Muestra> muestras = new ArrayList<>(ResumenDeLuzPorHora.comoSerie(
                resumenes.deLasHoras(plantaId, primeraCerrada, horaEnCurso), primeraCerrada));
        muestras.addAll(crudas(plantaId, magnitud, horaEnCurso, hasta).muestras());
        return SerieTemporal.de(muestras);
    }

    private SerieTemporal crudas(UUID plantaId, Magnitud magnitud, Instant desde, Instant hasta) {
        return SerieTemporal.de(jpa.serie(plantaId, magnitud, desde, hasta).stream()
                .map(fila -> new Muestra((Instant) fila[0], ((Number) fila[1]).doubleValue()))
                .toList());
    }

    @Override
    @Transactional
    public void guardar(DiagnosticoDePlanta diagnostico) {
        DiagnosticoDePlantaEntity entidad = jpa.findById(diagnostico.id()).orElseGet(mapeador::nuevaEntidad);
        mapeador.volcarEn(entidad, diagnostico);
        jpa.saveAndFlush(entidad);
        // Fase 12 (D175): una lectura que llega a una hora que ya cerró (cola sin red, reloj de la matera) cambia lo que
        // ese resumen debía decir. En el camino normal tomada y registrada caen en la misma hora y no cuesta nada.
        Instant horaTomada = ResumenesDeLuz.inicioDeLaHora(diagnostico.tomadaEn());
        if (!horaTomada.equals(ResumenesDeLuz.inicioDeLaHora(diagnostico.registradoEn()))) {
            resumenes.invalidar(diagnostico.plantaId(), horaTomada);
        }
    }

    @Override
    @Transactional
    public void eliminarDe(UUID plantaId) {
        jpa.borrarDeLaPlanta(plantaId);
    }
}
