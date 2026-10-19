package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.puerto.entrada.RegistrarMedicionManualPort;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.RegistrarLecturaPort;
import com.vivero.fitodiagnostico.dominio.excepcion.PlantaNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.modelo.Aviso;
import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import com.vivero.fitodiagnostico.dominio.modelo.OrigenDeMedicion;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import com.vivero.fitodiagnostico.dominio.puerto.salida.Notificador;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioAccesos;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioDiagnosticos;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioPlantas;
import com.vivero.fitodiagnostico.dominio.servicio.DetectorDeCambioDeEstado;
import com.vivero.fitodiagnostico.dominio.servicio.EvaluadorDeEstado;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAcceso;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * El caso de uso de registrar una medición sobre una planta (RF-F1) con sus
 * dos efectos: diagnosticar y guardar en el historial (RF-F3), y avisar: siempre
 * que hay un diagnóstico nuevo, para la vista en vivo (RF-G2, D62), y además si
 * el estado cambió (RF-G1). Es el ÚNICO camino que escribe en el historial (D3).
 *
 * <p>Un caso de uso, dos adaptadores de entrada (D17): hoy entra por
 * {@link #registrarManual} desde HTTP; el módulo de macetas entrará por
 * {@link #registrar(UUID, Medicion, OrigenDeMedicion)} desde su canal, sin
 * tocar el dominio ni este servicio. La comprobación de quién puede medir es
 * parte del adaptador de entrada humano y no del caso de uso: una maceta no es
 * un usuario, se autentica con su credencial (RN19).
 */
@Service
public class ServicioMediciones implements RegistrarMedicionManualPort, RegistrarLecturaPort {

    private final RepositorioPlantas plantas;
    private final RepositorioAccesos accesos;
    private final RepositorioDiagnosticos diagnosticos;
    private final EvaluadorDeEstado evaluador;
    private final DetectorDeCambioDeEstado detector;
    private final Notificador notificador;
    private final AutorizadorDePlantas autorizador;

    public ServicioMediciones(RepositorioPlantas plantas, RepositorioAccesos accesos,
                              RepositorioDiagnosticos diagnosticos, EvaluadorDeEstado evaluador,
                              DetectorDeCambioDeEstado detector, Notificador notificador,
                              PoliticaDeAcceso politica) {
        this.plantas = plantas;
        this.accesos = accesos;
        this.diagnosticos = diagnosticos;
        this.evaluador = evaluador;
        this.detector = detector;
        this.notificador = notificador;
        this.autorizador = new AutorizadorDePlantas(plantas, accesos, politica);
    }

    /**
     * RF-F1, origen MANUAL: solo el dueño mide (RN5). Un invitado recibe
     * {@code OperacionNoPermitidaException} y un ajeno, la misma
     * {@code PlantaNoEncontradaException} que si la planta no existiera.
     */
    @Transactional
    @Override
    public DiagnosticoDePlanta registrarManual(UUID solicitante, UUID plantaId, Medicion medicion) {
        Planta planta = autorizador.paraModificar(solicitante, plantaId);
        return registrar(planta, medicion, OrigenDeMedicion.MANUAL);
    }

    /**
     * El caso de uso, sin comprobar permisos: quien lo llame responde de haber
     * autenticado el origen (dueño por sesión, o maceta vinculada por credencial).
     *
     * @throws PlantaNoEncontradaException si la planta no existe
     */
    @Transactional
    @Override
    public DiagnosticoDePlanta registrar(UUID plantaId, Medicion medicion, OrigenDeMedicion origen) {
        Planta planta = plantas.buscar(plantaId).orElseThrow(() -> new PlantaNoEncontradaException(plantaId));
        return registrar(planta, medicion, origen);
    }

    private DiagnosticoDePlanta registrar(Planta planta, Medicion medicion, OrigenDeMedicion origen) {
        // RN8: se compara contra el inmediatamente anterior de ESTA planta, leído antes de guardar el nuevo.
        Optional<DiagnosticoDePlanta> anterior = diagnosticos.ultimoDe(planta.id());

        // RN11: se evalúa con la especie de la planta aunque ya esté descontinuada.
        Diagnostico evaluado = evaluador.evaluar(planta.especie(), medicion);
        DiagnosticoDePlanta registro = DiagnosticoDePlanta.deUnaEvaluacion(planta.id(), origen, evaluado);
        diagnosticos.guardar(registro);

        // RF-G2: toda medición nueva se refleja en la vista en vivo, haya cambiado el estado o no.
        Set<UUID> destinatarios = destinatariosDe(planta);
        notificador.avisar(new Aviso(destinatarios, TipoDeAviso.DIAGNOSTICO_NUEVO,
                "Nuevo diagnóstico de la planta «" + planta.apodo() + "»: " + registro.estado() + ".",
                Map.of(Aviso.PLANTA_ID, planta.id().toString(),
                        Aviso.ESTADO, registro.estado().name(),
                        Aviso.REGISTRADO_EN, registro.registradoEn().toString(),
                        Aviso.ORIGEN, origen.name())));

        // RF-G1: solo si el estado cambió respecto del inmediatamente anterior (RN8).
        if (detector.huboCambio(anterior.map(DiagnosticoDePlanta::diagnostico).orElse(null), evaluado)) {
            notificador.avisar(new Aviso(destinatarios, TipoDeAviso.CAMBIO_DE_ESTADO,
                    "La planta «" + planta.apodo() + "» pasó de " + anterior.orElseThrow().estado()
                            + " a " + registro.estado() + ".",
                    Map.of(Aviso.PLANTA_ID, planta.id().toString(),
                            Aviso.ESTADO, registro.estado().name())));
        }
        return registro;
    }

    /** RF-G1: el dueño y los invitados. */
    private Set<UUID> destinatariosDe(Planta planta) {
        Set<UUID> destinatarios = new HashSet<>(accesos.invitadosDe(planta.id()));
        destinatarios.add(planta.duenoId());
        return destinatarios;
    }
}
