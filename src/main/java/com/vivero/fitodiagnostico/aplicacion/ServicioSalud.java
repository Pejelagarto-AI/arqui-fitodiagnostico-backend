package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.modelo.Dispositivo;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioAccesos;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioDispositivos;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioPlantas;
import com.vivero.fitodiagnostico.salud.AvisadorDeSalud;
import com.vivero.fitodiagnostico.salud.ConsultarSaludPort;
import com.vivero.fitodiagnostico.salud.EstadoDeSalud;
import com.vivero.fitodiagnostico.salud.EstadoDeSensor;
import com.vivero.fitodiagnostico.salud.RepositorioDeSalud;
import com.vivero.fitodiagnostico.salud.ReportarSaludPort;
import com.vivero.fitodiagnostico.salud.RevisarSilencioPort;
import com.vivero.fitodiagnostico.salud.SaludDelDispositivo;
import com.vivero.fitodiagnostico.salud.SensorDeMatera;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Los casos de uso de la salud de la matera (fase 10): lo que ella dice de sus sensores, el silencio que el servidor
 * detecta y la consulta para las pantallas. Se arma en {@code ConfiguracionSalud}, que le pasa los tiempos.
 *
 * <p>Cuándo se considera que una matera calló (D156):
 * <ul>
 *   <li>Con salud {@code OK}: más de {@code max(3 x intervalo esperado, mínimo)} sin lectura ni reporte. El mínimo
 *       (2 min por defecto) evita avisos por una red lenta cuando el intervalo de la demo es de 5 s.</li>
 *   <li>Con salud {@code FALLA}: la matera no manda lecturas, solo el reporte de salud cada {@code latidoDeFalla}
 *       (5 min). Su intervalo esperado es ese, así que el tope es {@code 3 x latidoDeFalla}.</li>
 * </ul>
 *
 * <p>No guarda lógica de comparación de lecturas ni toca el diagnóstico: la salud del aparato es un asunto aparte
 * de la salud de la planta (D150).
 */
@Transactional
public class ServicioSalud implements ReportarSaludPort, ConsultarSaludPort, RevisarSilencioPort {

    public static final int MULTIPLO_DE_SILENCIO = 3;

    private final RepositorioDeSalud salud;
    private final RepositorioDispositivos dispositivos;
    private final RepositorioPlantas plantas;
    private final RepositorioAccesos accesos;
    private final AvisadorDeSalud avisador;
    private final Clock reloj;
    private final Duration silencioConSalud;
    private final Duration silencioConFalla;

    public ServicioSalud(RepositorioDeSalud salud, RepositorioDispositivos dispositivos, RepositorioPlantas plantas,
                         RepositorioAccesos accesos, AvisadorDeSalud avisador, Clock reloj,
                         Duration intervaloEsperado, Duration silencioMinimo, Duration latidoDeFalla) {
        this.salud = salud;
        this.dispositivos = dispositivos;
        this.plantas = plantas;
        this.accesos = accesos;
        this.avisador = avisador;
        this.reloj = reloj;
        Duration porIntervalo = intervaloEsperado.multipliedBy(MULTIPLO_DE_SILENCIO);
        this.silencioConSalud = porIntervalo.compareTo(silencioMinimo) >= 0 ? porIntervalo : silencioMinimo;
        this.silencioConFalla = latidoDeFalla.multipliedBy(MULTIPLO_DE_SILENCIO);
    }

    @Override
    public Resultado reportar(String serial, UUID plantaId, Map<SensorDeMatera, EstadoDeSensor> sensores,
                              String detalle) {
        Instant ahora = ahora();
        EstadoDeSalud nuevo = sensores.containsValue(EstadoDeSensor.FALLA) ? EstadoDeSalud.FALLA : EstadoDeSalud.OK;
        Optional<SaludDelDispositivo> anterior = salud.de(serial);

        boolean cambio = anterior.map(a -> a.estado() != nuevo || !a.sensores().equals(SaludDelDispositivo.completar(sensores)))
                .orElse(nuevo == EstadoDeSalud.FALLA);
        Instant cambioEn = cambio || anterior.isEmpty() ? ahora : anterior.get().cambioEn();
        SaludDelDispositivo guardada = anterior
                .map(a -> a.conReporte(nuevo, sensores, detalleLimpio(detalle, nuevo), ahora, cambioEn))
                .orElseGet(() -> new SaludDelDispositivo(serial, nuevo, sensores, detalleLimpio(detalle, nuevo),
                        ahora, null, cambioEn));
        salud.guardar(guardada);
        if (cambio) {
            avisar(plantaId, guardada, ahora);
        }
        return new Resultado(nuevo, cambio);
    }

    @Override
    public void registrarLectura(String serial, UUID plantaId) {
        Instant ahora = ahora();
        Optional<SaludDelDispositivo> anterior = salud.de(serial);
        if (anterior.isEmpty()) {
            salud.guardar(new SaludDelDispositivo(serial, EstadoDeSalud.OK, SaludDelDispositivo.todosOk(), null,
                    null, ahora, ahora));
            return;
        }
        SaludDelDispositivo actual = anterior.get();
        if (actual.estado() == EstadoDeSalud.OK) {
            salud.guardar(actual.conLectura(ahora));
            return;
        }
        // Llegó una lectura completa: todos los sensores respondieron y la matera volvió a hablar.
        SaludDelDispositivo recuperada = new SaludDelDispositivo(serial, EstadoDeSalud.OK,
                SaludDelDispositivo.todosOk(), null, actual.reportadoEn(), ahora, ahora);
        salud.guardar(recuperada);
        avisar(plantaId, recuperada, ahora);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Vista> deLosDispositivos(Collection<String> seriales) {
        Instant ahora = ahora();
        return salud.de(seriales).stream().collect(Collectors.toMap(SaludDelDispositivo::serial,
                s -> new Vista(s, s.mensaje(ahora)), (a, b) -> a, LinkedHashMap::new));
    }

    @Override
    public int revisar() {
        Instant ahora = ahora();
        Map<String, Dispositivo> vinculados = dispositivos.todos().stream().filter(Dispositivo::estaReclamado)
                .collect(Collectors.toMap(Dispositivo::serial, d -> d));
        int callaron = 0;
        for (SaludDelDispositivo s : salud.de(vinculados.keySet())) {
            if (s.estado() == EstadoDeSalud.SIN_NOTICIAS) {
                continue;
            }
            Duration tope = s.estado() == EstadoDeSalud.FALLA ? silencioConFalla : silencioConSalud;
            boolean callo = s.ultimaNoticiaEn().map(t -> Duration.between(t, ahora).compareTo(tope) > 0).orElse(false);
            if (callo) {
                SaludDelDispositivo muda = s.sinNoticias(ahora);
                salud.guardar(muda);
                avisar(vinculados.get(s.serial()).plantaId().orElseThrow(), muda, ahora);
                callaron++;
            }
        }
        return callaron;
    }

    // ---- avisos ----

    private void avisar(UUID plantaId, SaludDelDispositivo estado, Instant ahora) {
        Optional<Planta> planta = plantas.buscar(plantaId);
        if (planta.isEmpty()) {
            return;
        }
        Set<UUID> destinatarios = new HashSet<>(accesos.invitadosDe(plantaId));
        destinatarios.add(planta.get().duenoId());
        avisador.avisar(new AvisadorDeSalud.Aviso(plantaId, destinatarios, estado, estado.mensaje(ahora)));
    }

    // ---- utilidades ----

    /** El detalle solo tiene sentido mientras algo falla: al recuperarse se borra. */
    private static String detalleLimpio(String detalle, EstadoDeSalud estado) {
        return estado == EstadoDeSalud.OK || detalle == null || detalle.isBlank() ? null : detalle.strip();
    }

    /** A microsegundos, la precisión de la columna (como {@code ServicioDispositivos}). */
    private Instant ahora() {
        return reloj.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
