package com.vivero.fitodiagnostico.mascota.aplicacion;

import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.mascota.modelo.Accesorio;
import com.vivero.fitodiagnostico.mascota.modelo.EstadoDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.EventoDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.MapaDeAnimo;
import com.vivero.fitodiagnostico.mascota.modelo.ParametrosDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.ResumenDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.VistaParaLaMatera;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioDiagnosticos;
import com.vivero.fitodiagnostico.mascota.puerto.entrada.ConsultarMascotaDeMateraPort;
import com.vivero.fitodiagnostico.mascota.puerto.entrada.ConsultarResumenDeMascotasPort;
import com.vivero.fitodiagnostico.mascota.puerto.salida.RepositorioEventosDeMascota;
import com.vivero.fitodiagnostico.mascota.puerto.salida.RepositorioMascotas;
import com.vivero.fitodiagnostico.mascota.puerto.salida.UltimosDiagnosticosEnLote;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Las vistas recortadas de la mascota (fase 13): la que lee la matera en la respuesta de cada lectura y la del
 * tablero de plantas por lote. Solo lee. Está aparte de {@link ServicioConsultaDeMascota} (la consulta del dueño, con
 * autorización y línea de tiempo) porque estas dos no autorizan personas: el vínculo del dispositivo y el listado ya
 * filtraron lo que se puede ver.
 */
@Service
public class ServicioVistasDeMascota implements ConsultarMascotaDeMateraPort, ConsultarResumenDeMascotasPort {

    /** Cuántos eventos le caben a la respuesta de una lectura (D180). */
    public static final int EVENTOS_PARA_LA_MATERA = 10;
    /** Sin lectura anterior de esa matera, cuánto hacia atrás se miran los eventos (D180). */
    public static final Duration VENTANA_SIN_LECTURA_ANTERIOR = Duration.ofSeconds(60);

    private final RepositorioMascotas mascotas;
    private final RepositorioEventosDeMascota eventos;
    private final RepositorioDiagnosticos diagnosticos;
    private final UltimosDiagnosticosEnLote ultimos;
    private final ParametrosDeMascota parametros;
    private final MapaDeAnimo animo;
    private final Clock reloj;

    public ServicioVistasDeMascota(RepositorioMascotas mascotas, RepositorioEventosDeMascota eventos,
                                   RepositorioDiagnosticos diagnosticos, UltimosDiagnosticosEnLote ultimos,
                                   ParametrosDeMascota parametros, Clock reloj) {
        this.mascotas = mascotas;
        this.eventos = eventos;
        this.diagnosticos = diagnosticos;
        this.ultimos = ultimos;
        this.parametros = parametros;
        this.animo = new MapaDeAnimo(parametros);
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    @Override
    public VistaParaLaMatera paraLaMatera(UUID plantaId, Instant ultimaLecturaAnterior) {
        Optional<EstadoDeMascota> estado = mascotas.de(plantaId);
        Optional<Diagnostico> ultimo = diagnosticos.ultimoDe(plantaId).map(DiagnosticoDePlanta::diagnostico);
        Instant desde = ultimaLecturaAnterior != null ? ultimaLecturaAnterior
                : reloj.instant().minus(VENTANA_SIN_LECTURA_ANTERIOR);
        List<EventoDeMascota> nuevos = eventos.registradosDespuesDe(plantaId, desde, EVENTOS_PARA_LA_MATERA);
        return new VistaParaLaMatera(resumen(estado, ultimo), nuevos);
    }

    @Transactional(readOnly = true)
    @Override
    public Map<UUID, ResumenDeMascota> deLasPlantas(Collection<UUID> plantaIds) {
        if (plantaIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, EstadoDeMascota> estados = mascotas.deLasPlantas(plantaIds);
        if (estados.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Diagnostico> diagnosticosDeLasPlantas = ultimos.ultimosDe(estados.keySet());
        Map<UUID, ResumenDeMascota> resumenes = new HashMap<>();
        estados.forEach((planta, estado) -> resumenes.put(planta,
                resumen(Optional.of(estado), Optional.ofNullable(diagnosticosDeLasPlantas.get(planta)))));
        return resumenes;
    }

    private ResumenDeMascota resumen(Optional<EstadoDeMascota> estado, Optional<Diagnostico> ultimo) {
        return new ResumenDeMascota(
                estado.map(EstadoDeMascota::vitalidadEntera).orElse((int) Math.round(parametros.vitalidadInicial())),
                animo.de(ultimo, estado).animo(),
                estado.map(EstadoDeMascota::rachaDias).orElse(0),
                estado.map(EstadoDeMascota::accesorio).orElse(Accesorio.NINGUNO));
    }
}
