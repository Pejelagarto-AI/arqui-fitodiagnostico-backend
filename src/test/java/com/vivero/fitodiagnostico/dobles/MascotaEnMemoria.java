package com.vivero.fitodiagnostico.dobles;

import com.vivero.fitodiagnostico.dominio.modelo.Pagina;
import com.vivero.fitodiagnostico.dominio.modelo.Paginacion;
import com.vivero.fitodiagnostico.mascota.modelo.AccionDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.AvisoDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.EstadoDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.EventoDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.TipoDeEvento;
import com.vivero.fitodiagnostico.mascota.puerto.salida.PublicadorDeMascota;
import com.vivero.fitodiagnostico.mascota.puerto.salida.RepositorioAccionesDeMascota;
import com.vivero.fitodiagnostico.mascota.puerto.salida.RepositorioEventosDeMascota;
import com.vivero.fitodiagnostico.mascota.puerto.salida.RepositorioMascotas;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Dobles en memoria de los tres repositorios y del publicador de la mascota, en un solo objeto. */
public class MascotaEnMemoria implements RepositorioMascotas, RepositorioEventosDeMascota,
        RepositorioAccionesDeMascota, PublicadorDeMascota {

    private final Map<UUID, EstadoDeMascota> mascotas = new LinkedHashMap<>();
    private final List<EventoDeMascota> eventos = new ArrayList<>();
    private final Map<UUID, AccionDeMascota> acciones = new LinkedHashMap<>();
    public final List<AvisoDeMascota> publicados = new ArrayList<>();

    @Override
    public Optional<EstadoDeMascota> de(UUID plantaId) {
        return Optional.ofNullable(mascotas.get(plantaId));
    }

    @Override
    public Optional<EstadoDeMascota> paraActualizar(UUID plantaId) {
        return de(plantaId);
    }

    @Override
    public void guardar(EstadoDeMascota estado) {
        mascotas.put(estado.plantaId(), estado);
    }

    @Override
    public List<UUID> todas() {
        return List.copyOf(mascotas.keySet());
    }

    @Override
    public void guardar(EventoDeMascota evento) {
        eventos.add(evento);
    }

    @Override
    public java.util.Optional<EventoDeMascota> ultimoDeTipo(UUID plantaId, com.vivero.fitodiagnostico.mascota.modelo.TipoDeEvento tipo) {
        return eventos.stream().filter(e -> e.plantaId().equals(plantaId) && e.tipo() == tipo)
                .max(Comparator.comparing(EventoDeMascota::ocurridoEn));
    }

    @Override
    public Pagina<EventoDeMascota> de(UUID plantaId, Paginacion paginacion) {
        List<EventoDeMascota> deLaPlanta = new ArrayList<>(eventos.stream()
                .filter(e -> e.plantaId().equals(plantaId)).toList());
        deLaPlanta.sort(Comparator.comparing(EventoDeMascota::ocurridoEn).reversed());
        int desde = (int) Math.min(paginacion.desplazamiento(), deLaPlanta.size());
        int hasta = Math.min(desde + paginacion.tamano(), deLaPlanta.size());
        return new Pagina<>(deLaPlanta.subList(desde, hasta), paginacion.pagina(), paginacion.tamano(),
                deLaPlanta.size());
    }

    @Override
    public void guardar(AccionDeMascota accion) {
        acciones.put(accion.id(), accion);
    }

    @Override
    public Optional<AccionDeMascota> pendienteDe(UUID plantaId) {
        return acciones.values().stream().filter(a -> a.plantaId().equals(plantaId) && a.pendiente()).findFirst();
    }

    @Override
    public void publicar(AvisoDeMascota aviso) {
        publicados.add(aviso);
    }

    public List<TipoDeEvento> tiposDe(UUID plantaId) {
        return eventos.stream().filter(e -> e.plantaId().equals(plantaId)).map(EventoDeMascota::tipo).toList();
    }

    public Optional<AccionDeMascota> accion(UUID id) {
        return Optional.ofNullable(acciones.get(id));
    }
}
