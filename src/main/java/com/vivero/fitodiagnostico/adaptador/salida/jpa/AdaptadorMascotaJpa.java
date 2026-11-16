package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivero.fitodiagnostico.dominio.modelo.Pagina;
import com.vivero.fitodiagnostico.dominio.modelo.Paginacion;
import com.vivero.fitodiagnostico.mascota.modelo.AccionDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.EstadoDeAccion;
import com.vivero.fitodiagnostico.mascota.modelo.EstadoDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.EventoDeMascota;
import com.vivero.fitodiagnostico.mascota.puerto.salida.RepositorioAccionesDeMascota;
import com.vivero.fitodiagnostico.mascota.puerto.salida.RepositorioEventosDeMascota;
import com.vivero.fitodiagnostico.mascota.puerto.salida.RepositorioMascotas;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JPA de los tres puertos de la mascota (V16 a V19). Un solo adaptador porque las tres tablas son del
 * mismo contexto y cambian juntas; la entidad no cruza el borde del paquete.
 *
 * <p>El detalle de un evento se guarda como JSON de texto a texto en una columna VARCHAR: es para mostrar, nadie
 * consulta por él con SQL, y así H2 y PostgreSQL lo tratan igual (el criterio contrario de la D39 aplicaba a las
 * magnitudes del diagnóstico, que sí se consultan).
 */
@Repository
public class AdaptadorMascotaJpa implements RepositorioMascotas, RepositorioEventosDeMascota, RepositorioAccionesDeMascota {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, String>> MAPA = new TypeReference<>() { };

    private final MascotaJpaRepository mascotas;
    private final EventoDeMascotaJpaRepository eventos;
    private final AccionDeMascotaJpaRepository acciones;
    private final Clock reloj;

    public AdaptadorMascotaJpa(MascotaJpaRepository mascotas, EventoDeMascotaJpaRepository eventos,
                               AccionDeMascotaJpaRepository acciones, Clock reloj) {
        this.mascotas = mascotas;
        this.eventos = eventos;
        this.acciones = acciones;
        this.reloj = reloj;
    }

    // ---- mascota ----

    @Override
    @Transactional(readOnly = true)
    public Optional<EstadoDeMascota> de(UUID plantaId) {
        return mascotas.findById(plantaId).map(AdaptadorMascotaJpa::aModelo);
    }

    @Override
    @Transactional
    public Optional<EstadoDeMascota> paraActualizar(UUID plantaId) {
        return mascotas.bloquear(plantaId).map(AdaptadorMascotaJpa::aModelo);
    }

    @Override
    @Transactional
    public void guardar(EstadoDeMascota e) {
        MascotaEntity fila = mascotas.findById(e.plantaId()).orElseGet(() -> new MascotaEntity(e.plantaId()));
        fila.volcar(e.vitalidad(), e.desmayada(), e.ultimoEstado(), e.ultimaLecturaEn(), e.actualizadaEn(),
                e.saludableDesde(), e.rachaDias(), e.rachaDesde(), e.ultimaRegadaEn(), e.ultimoSolEn());
        mascotas.saveAndFlush(fila);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> todas() {
        return mascotas.plantas();
    }

    // ---- eventos ----

    @Override
    @Transactional
    public void guardar(EventoDeMascota evento) {
        eventos.saveAndFlush(new EventoDeMascotaEntity(evento.id(), evento.plantaId(), evento.tipo(),
                evento.ocurridoEn(), reloj.instant(), aJson(evento.detalle())));
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<EventoDeMascota> de(UUID plantaId, Paginacion paginacion) {
        Page<EventoDeMascotaEntity> pagina = eventos.findByPlantaIdOrderByOcurridoEnDescRegistradoEnDesc(plantaId,
                PageRequest.of(paginacion.pagina(), paginacion.tamano()));
        return new Pagina<>(pagina.getContent().stream().map(AdaptadorMascotaJpa::aModelo).toList(),
                paginacion.pagina(), paginacion.tamano(), pagina.getTotalElements());
    }

    // ---- acciones ----

    @Override
    @Transactional
    public void guardar(AccionDeMascota a) {
        AccionDeMascotaEntity fila = acciones.findById(a.id()).orElseGet(() -> new AccionDeMascotaEntity(a.id()));
        fila.volcar(a.plantaId(), a.tipo(), a.estado(), a.creadaEn(), a.venceEn(), a.resueltaEn(),
                a.humedadDeReferencia());
        acciones.saveAndFlush(fila);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccionDeMascota> pendienteDe(UUID plantaId) {
        return acciones.findFirstByPlantaIdAndEstadoOrderByCreadaEnDesc(plantaId, EstadoDeAccion.PENDIENTE)
                .map(f -> new AccionDeMascota(f.getId(), f.getPlantaId(), f.getTipo(), f.getEstado(), f.getCreadaEn(),
                        f.getVenceEn(), f.getResueltaEn(), f.getHumedadReferencia()));
    }

    // ---- traducción ----

    private static EstadoDeMascota aModelo(MascotaEntity f) {
        return new EstadoDeMascota(f.getPlantaId(), f.getVitalidad(), f.isDesmayada(), f.getUltimoEstado(),
                f.getUltimaLecturaEn(), f.getActualizadaEn(), f.getSaludableDesde(), f.getRachaDias(),
                f.getRachaDesde(), f.getUltimaRegadaEn(), f.getUltimoSolEn());
    }

    private static EventoDeMascota aModelo(EventoDeMascotaEntity f) {
        return new EventoDeMascota(f.getId(), f.getPlantaId(), f.getTipo(), f.getOcurridoEn(), deJson(f.getDetalle()));
    }

    static String aJson(Map<String, String> detalle) {
        try {
            return JSON.writeValueAsString(detalle);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("no se pudo serializar el detalle del evento", e);
        }
    }

    static Map<String, String> deJson(String json) {
        try {
            return JSON.readValue(json, MAPA);
        } catch (JsonProcessingException e) {
            return Map.of("ilegible", json);
        }
    }
}
