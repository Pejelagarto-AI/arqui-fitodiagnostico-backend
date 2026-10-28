package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.puerto.entrada.SolicitarEspeciePort;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.ResolverSolicitudesDeEspeciePort;
import com.vivero.fitodiagnostico.dominio.excepcion.EspecieYaExisteException;
import com.vivero.fitodiagnostico.dominio.excepcion.SolicitudNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.modelo.Aviso;
import com.vivero.fitodiagnostico.dominio.modelo.SolicitudEspecie;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import com.vivero.fitodiagnostico.dominio.puerto.salida.Notificador;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RangosPorEspecie;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioSolicitudes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Solicitudes de especie nueva (RF-H1 a RF-H5). Cualquier cliente propone y
 * consulta las suyas; listar las pendientes y resolver es del administrador,
 * y eso lo garantiza el borde web (las rutas exigen el rol), igual que en
 * {@link ServicioAdministracion}.
 *
 * <p>Aprobar NO da de alta la especie (D55): ver {@link SolicitudEspecie}.
 */
@Service
public class ServicioSolicitudes implements SolicitarEspeciePort, ResolverSolicitudesDeEspeciePort {

    private final RepositorioSolicitudes solicitudes;
    private final RangosPorEspecie especies;
    private final Notificador notificador;
    private final Clock reloj;

    public ServicioSolicitudes(RepositorioSolicitudes solicitudes, RangosPorEspecie especies,
                               Notificador notificador, Clock reloj) {
        this.solicitudes = solicitudes;
        this.especies = especies;
        this.notificador = notificador;
        this.reloj = reloj;
    }

    /**
     * RF-H1. Pedir una especie que ya está en el catálogo (activa o no) no tiene
     * sentido: se le dice al solicitante en vez de dejar una solicitud que el
     * administrador tendría que rechazar a mano.
     *
     * @throws EspecieYaExisteException si el catálogo ya tiene una especie con ese nombre
     * @throws IllegalArgumentException si falta el nombre o algún texto es demasiado largo
     */
    @Transactional
    @Override
    public SolicitudEspecie enviar(UUID solicitanteId, String nombrePropuesto, String justificacion) {
        SolicitudEspecie nueva = SolicitudEspecie.nueva(solicitanteId, nombrePropuesto, justificacion,
                reloj.instant());
        if (especies.buscar(nueva.nombrePropuesto()).isPresent()) {
            throw new EspecieYaExisteException(nueva.nombrePropuesto());
        }
        solicitudes.guardar(nueva);
        return nueva;
    }

    /** RF-H2: las solicitudes propias con su estado y su respuesta. */
    @Transactional(readOnly = true)
    @Override
    public List<SolicitudEspecie> deSolicitante(UUID solicitanteId) {
        return solicitudes.deSolicitante(solicitanteId);
    }

    /** RF-H3: las pendientes, la más antigua primero. */
    @Transactional(readOnly = true)
    @Override
    public List<SolicitudEspecie> pendientes() {
        return solicitudes.pendientes();
    }

    /**
     * RF-H4 (aprobar) y RF-H5. La respuesta es opcional.
     *
     * @throws SolicitudNoEncontradaException si no existe
     * @throws com.vivero.fitodiagnostico.dominio.excepcion.SolicitudYaResueltaException si ya estaba resuelta
     */
    @Transactional
    @Override
    public SolicitudEspecie aprobar(UUID solicitudId, String respuesta) {
        SolicitudEspecie aprobada = cargar(solicitudId).aprobada(respuesta, reloj.instant());
        return resolver(aprobada, "aprobada");
    }

    /**
     * RF-H4 (rechazar) y RF-H5. La respuesta con el motivo es obligatoria.
     *
     * @throws IllegalArgumentException si la respuesta está vacía
     */
    @Transactional
    @Override
    public SolicitudEspecie rechazar(UUID solicitudId, String respuesta) {
        SolicitudEspecie rechazada = cargar(solicitudId).rechazada(respuesta, reloj.instant());
        return resolver(rechazada, "rechazada");
    }

    private SolicitudEspecie resolver(SolicitudEspecie resuelta, String verbo) {
        solicitudes.guardar(resuelta);
        String texto = "Tu solicitud de la especie «" + resuelta.nombrePropuesto() + "» fue " + verbo + "."
                + (resuelta.respuesta() == null ? "" : " " + resuelta.respuesta());
        notificador.avisar(new Aviso(Set.of(resuelta.solicitanteId()), TipoDeAviso.SOLICITUD_RESUELTA, texto,
                Map.of(Aviso.SOLICITUD_ID, resuelta.id().toString(),
                        Aviso.RESOLUCION, resuelta.estado().name())));
        return resuelta;
    }

    private SolicitudEspecie cargar(UUID id) {
        return solicitudes.buscar(id).orElseThrow(() -> new SolicitudNoEncontradaException(id));
    }
}
