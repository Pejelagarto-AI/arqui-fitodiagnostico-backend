package com.vivero.fitodiagnostico.mascota.aplicacion;

import com.vivero.fitodiagnostico.aplicacion.AutorizadorDePlantas;
import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Dispositivo;
import com.vivero.fitodiagnostico.dominio.modelo.Paginacion;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioAccesos;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioDiagnosticos;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioDispositivos;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioPlantas;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAcceso;
import com.vivero.fitodiagnostico.mascota.modelo.AccionDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.AvisoDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.ConfirmadorDeRiego;
import com.vivero.fitodiagnostico.mascota.modelo.DetectorDeSaltos;
import com.vivero.fitodiagnostico.mascota.modelo.EstadoDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.EstadoDeAccion;
import com.vivero.fitodiagnostico.mascota.modelo.EventoDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.MapaDeAnimo;
import com.vivero.fitodiagnostico.mascota.modelo.LecturaReciente;
import com.vivero.fitodiagnostico.mascota.modelo.ParametrosDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.ReglasDeVitalidad;
import com.vivero.fitodiagnostico.mascota.modelo.ResultadoDeAccion;
import com.vivero.fitodiagnostico.mascota.modelo.TipoDeAccion;
import com.vivero.fitodiagnostico.mascota.modelo.TipoDeEvento;
import com.vivero.fitodiagnostico.mascota.puerto.entrada.ActuarSobreMascotaPort;
import com.vivero.fitodiagnostico.mascota.puerto.entrada.AlimentarMascotaPort;
import com.vivero.fitodiagnostico.mascota.puerto.entrada.AvanzarMascotasPort;
import com.vivero.fitodiagnostico.mascota.puerto.salida.PublicadorDeMascota;
import com.vivero.fitodiagnostico.mascota.puerto.salida.RepositorioAccionesDeMascota;
import com.vivero.fitodiagnostico.mascota.puerto.salida.RepositorioEventosDeMascota;
import com.vivero.fitodiagnostico.mascota.puerto.salida.RepositorioMascotas;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Los casos de uso que ESCRIBEN la mascota: alimentarla con cada lectura, avanzar el tiempo y las acciones del
 * dueño. Orquesta; las reglas viven en el modelo ({@link ReglasDeVitalidad}, {@link DetectorDeSaltos},
 * {@link ConfirmadorDeRiego}, {@link MapaDeAnimo}).
 *
 * <p>Consume el historial por los puertos que ya existían ({@link RepositorioDiagnosticos}, {@link RepositorioPlantas},
 * {@link RepositorioAccesos}, {@link RepositorioDispositivos}) y escribe solo en los suyos. No llama al evaluador
 * ni al notificador del diagnóstico: no puede producir ni avisar un diagnóstico.
 */
@Service
public class ServicioMascota implements AlimentarMascotaPort, AvanzarMascotasPort, ActuarSobreMascotaPort {

    private final RepositorioPlantas plantas;
    private final RepositorioAccesos accesos;
    private final RepositorioDiagnosticos diagnosticos;
    private final RepositorioDispositivos dispositivos;
    private final RepositorioMascotas mascotas;
    private final RepositorioEventosDeMascota eventos;
    private final RepositorioAccionesDeMascota acciones;
    private final PublicadorDeMascota publicador;
    private final AutorizadorDePlantas autorizador;
    private final ParametrosDeMascota parametros;
    private final ReglasDeVitalidad vitalidad;
    private final DetectorDeSaltos detector;
    private final ConfirmadorDeRiego confirmador;
    private final MapaDeAnimo animo;
    private final Clock reloj;

    public ServicioMascota(RepositorioPlantas plantas, RepositorioAccesos accesos, RepositorioDiagnosticos diagnosticos,
                           RepositorioDispositivos dispositivos, PoliticaDeAcceso politica,
                           RepositorioMascotas mascotas, RepositorioEventosDeMascota eventos,
                           RepositorioAccionesDeMascota acciones, PublicadorDeMascota publicador,
                           ParametrosDeMascota parametros, Clock reloj) {
        this.plantas = plantas;
        this.accesos = accesos;
        this.diagnosticos = diagnosticos;
        this.dispositivos = dispositivos;
        this.mascotas = mascotas;
        this.eventos = eventos;
        this.acciones = acciones;
        this.publicador = publicador;
        this.autorizador = new AutorizadorDePlantas(plantas, accesos, politica);
        this.parametros = parametros;
        this.vitalidad = new ReglasDeVitalidad(parametros);
        this.detector = new DetectorDeSaltos(parametros);
        this.confirmador = new ConfirmadorDeRiego(parametros);
        this.animo = new MapaDeAnimo(parametros);
        this.reloj = reloj;
    }

    /**
     * Una lectura ya guardada. Corre en su PROPIA transacción (la llaman después del commit de la lectura, D140):
     * si algo de la mascota falla, la lectura ya quedó.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void alRegistrarLectura(DiagnosticoDePlanta registro) {
        Optional<Planta> planta = plantas.buscar(registro.plantaId());
        if (planta.isEmpty()) {
            return;   // la borraron entre la lectura y este paso
        }
        Instant ahora = reloj.instant();
        EstadoDeMascota antes = mascotas.paraActualizar(registro.plantaId())
                .orElseGet(() -> EstadoDeMascota.nueva(registro.plantaId(), ahora, parametros));
        ReglasDeVitalidad.Avance avance = vitalidad.alimentar(antes, registro.estado(), ahora);
        EstadoDeMascota estado = avance.estado();
        List<EventoDeMascota> nuevos = new ArrayList<>(avance.eventos());

        List<LecturaReciente> muestras = muestrasRecientes(registro.plantaId());
        Optional<EventoDeMascota> regada = detector.riego(registro.plantaId(), muestras, estado.ultimaRegadaEn());
        if (regada.isPresent()) {
            nuevos.add(regada.get());
            estado = estado.conRegadaEn(regada.get().ocurridoEn());
        }
        Optional<EventoDeMascota> sol = detector.sol(registro.plantaId(), muestras, estado.ultimoSolEn());
        if (sol.isPresent()) {
            nuevos.add(sol.get());
            estado = estado.conSolEn(sol.get().ocurridoEn());
        }
        resolverPendiente(registro.plantaId(), muestras, estado, ahora).ifPresent(nuevos::add);

        mascotas.guardar(estado);
        registrarYPublicar(planta.get(), estado, Optional.of(registro.diagnostico()), nuevos);
    }

    @Transactional(readOnly = true)
    @Override
    public List<UUID> mascotasActivas() {
        return mascotas.todas();
    }

    /** El tic de una planta: integra la vitalidad hasta ahora y vence el "la regué" si toca. */
    @Transactional
    @Override
    public void avanzar(UUID plantaId) {
        Optional<Planta> planta = plantas.buscar(plantaId);
        Optional<EstadoDeMascota> antes = mascotas.paraActualizar(plantaId);
        if (planta.isEmpty() || antes.isEmpty()) {
            return;
        }
        Instant ahora = reloj.instant();
        ReglasDeVitalidad.Avance avance = vitalidad.avanzar(antes.get(), ahora);
        List<EventoDeMascota> nuevos = new ArrayList<>(avance.eventos());
        Optional<AccionDeMascota> pendiente = acciones.pendienteDe(plantaId);
        if (pendiente.isPresent()) {
            List<LecturaReciente> muestras = muestrasRecientes(plantaId);
            resolverPendiente(plantaId, muestras, avance.estado(), ahora).ifPresent(nuevos::add);
        }
        // Escribir solo si algo cambió: una mascota SALUDABLE con 100 de vitalidad no genera una escritura por minuto.
        if (!nuevos.isEmpty() || Math.abs(avance.estado().vitalidad() - antes.get().vitalidad()) >= 0.01) {
            mascotas.guardar(avance.estado());
        }
        if (!nuevos.isEmpty()) {
            registrarYPublicar(planta.get(), avance.estado(), diagnosticos.ultimoDe(plantaId).map(DiagnosticoDePlanta::diagnostico),
                    nuevos);
        }
    }

    @Transactional
    @Override
    public ResultadoDeAccion actuar(UUID solicitante, UUID plantaId, TipoDeAccion tipo) {
        Planta planta = autorizador.paraModificar(solicitante, plantaId);
        Instant ahora = reloj.instant();
        // Con bloqueo: dos caricias a la vez de la misma planta no se cuelan las dos por el enfriamiento (D172).
        Optional<EstadoDeMascota> estado = mascotas.paraActualizar(plantaId);
        Optional<Diagnostico> ultimo = diagnosticos.ultimoDe(plantaId).map(DiagnosticoDePlanta::diagnostico);
        EstadoDeMascota paraAvisar = estado.orElseGet(() -> EstadoDeMascota.nueva(plantaId, ahora, parametros));

        if (tipo == TipoDeAccion.ACARICIAR) {
            Optional<EventoDeMascota> anterior = eventos.ultimoDeTipo(plantaId, TipoDeEvento.ACARICIADA);
            if (anterior.isPresent() && Duration.between(anterior.get().ocurridoEn(), ahora)
                    .compareTo(parametros.enfriamientoDeCaricia()) < 0) {
                // Demasiado pronto: responde con la última, sin evento, sin aviso y sin mensaje a la matera.
                return new ResultadoDeAccion(anterior.get().id(), tipo, EstadoDeAccion.HECHA,
                        anterior.get().ocurridoEn(), null, true);
            }
            EventoDeMascota caricia = EventoDeMascota.nuevo(plantaId, TipoDeEvento.ACARICIADA, ahora, Map.of());
            registrarYPublicar(planta, paraAvisar, ultimo, List.of(caricia));
            return new ResultadoDeAccion(caricia.id(), tipo, EstadoDeAccion.HECHA,
                    ahora, null);
        }

        Optional<AccionDeMascota> yaPendiente = acciones.pendienteDe(plantaId);
        if (yaPendiente.isPresent()) {
            return ResultadoDeAccion.de(yaPendiente.get());   // idempotente: decirlo dos veces no abre dos esperas
        }
        List<LecturaReciente> muestras = muestrasRecientes(plantaId);
        AccionDeMascota accion = AccionDeMascota.riegoDeclarado(plantaId, ahora,
                ahora.plus(parametros.plazoParaConfirmarRiego()),
                detector.humedadDeReferencia(muestras, ahora).orElse(null));
        List<EventoDeMascota> nuevos = new ArrayList<>();
        nuevos.add(EventoDeMascota.nuevo(plantaId, TipoDeEvento.RIEGO_DECLARADO, ahora,
                Map.of("accionId", accion.id().toString(), "venceEn", accion.venceEn().toString())));
        // Si el sensor ya vio el riego (el dueño avisó después de regar), se confirma de una vez.
        Optional<ConfirmadorDeRiego.Resolucion> resolucion = confirmador.evaluar(accion, muestras,
                estado.map(EstadoDeMascota::ultimaRegadaEn).orElse(null), ahora);
        AccionDeMascota guardada = resolucion.map(ConfirmadorDeRiego.Resolucion::accion).orElse(accion);
        resolucion.ifPresent(r -> nuevos.add(r.evento()));
        acciones.guardar(guardada);
        registrarYPublicar(planta, paraAvisar, ultimo, nuevos);
        return ResultadoDeAccion.de(guardada);
    }

    private Optional<EventoDeMascota> resolverPendiente(UUID plantaId, List<LecturaReciente> muestras, EstadoDeMascota estado,
                                                        Instant ahora) {
        return acciones.pendienteDe(plantaId)
                .flatMap(p -> confirmador.evaluar(p, muestras, estado.ultimaRegadaEn(), ahora))
                .map(r -> {
                    acciones.guardar(r.accion());
                    return r.evento();
                });
    }

    /** Las últimas lecturas de la planta, por el puerto del historial que ya existía (una página). */
    private List<LecturaReciente> muestrasRecientes(UUID plantaId) {
        return diagnosticos.historialDe(plantaId, new Paginacion(0, parametros.lecturasARevisar()))
                .elementos().stream().map(LecturaReciente::de).toList();
    }

    private void registrarYPublicar(Planta planta, EstadoDeMascota estado, Optional<Diagnostico> ultimo,
                                    List<EventoDeMascota> nuevos) {
        if (nuevos.isEmpty()) {
            return;
        }
        Set<UUID> destinatarios = new HashSet<>(accesos.invitadosDe(planta.id()));
        destinatarios.add(planta.duenoId());
        Set<String> seriales = dispositivos.porPlanta(planta.id()).stream().map(Dispositivo::serial)
                .collect(Collectors.toSet());
        MapaDeAnimo.Resultado comoSeVe = animo.de(ultimo, Optional.of(estado));
        for (EventoDeMascota evento : nuevos) {
            eventos.guardar(evento);
            publicador.publicar(new AvisoDeMascota(evento, planta.apodo(), estado.vitalidadEntera(),
                    comoSeVe.animo(), destinatarios, seriales));
        }
    }
}
