package com.vivero.fitodiagnostico.mascota.aplicacion;

import com.vivero.fitodiagnostico.aplicacion.AutorizadorDePlantas;
import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Pagina;
import com.vivero.fitodiagnostico.dominio.modelo.Paginacion;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioAccesos;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioDiagnosticos;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioPlantas;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAcceso;
import com.vivero.fitodiagnostico.mascota.modelo.Accesorio;
import com.vivero.fitodiagnostico.mascota.modelo.EstadoDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.EventoDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.MapaDeAnimo;
import com.vivero.fitodiagnostico.mascota.modelo.ParametrosDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.VistaDeMascota;
import com.vivero.fitodiagnostico.mascota.puerto.entrada.ConsultarMascotaPort;
import com.vivero.fitodiagnostico.mascota.puerto.salida.RepositorioAccionesDeMascota;
import com.vivero.fitodiagnostico.mascota.puerto.salida.RepositorioEventosDeMascota;
import com.vivero.fitodiagnostico.mascota.puerto.salida.RepositorioMascotas;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Las consultas de la mascota, separadas de {@link ServicioMascota} por la misma razón que la D3 separa la consulta
 * del diagnóstico: este servicio no recibe el publicador ni los puertos de escritura de acciones más allá de leer, y
 * todo es {@code readOnly}. Devuelve lo guardado; no extrapola la vitalidad al instante de la consulta (la tarea
 * periódica la mantiene al día, con un minuto de retraso a lo sumo).
 */
@Service
public class ServicioConsultaDeMascota implements ConsultarMascotaPort {

    /** Cuántos eventos trae la consulta de la mascota; el resto, por la línea de tiempo paginada. */
    public static final int EVENTOS_EN_LA_VISTA = 5;

    private final RepositorioDiagnosticos diagnosticos;
    private final RepositorioMascotas mascotas;
    private final RepositorioEventosDeMascota eventos;
    private final RepositorioAccionesDeMascota acciones;
    private final AutorizadorDePlantas autorizador;
    private final ParametrosDeMascota parametros;
    private final MapaDeAnimo animo;

    public ServicioConsultaDeMascota(RepositorioPlantas plantas, RepositorioAccesos accesos,
                                     RepositorioDiagnosticos diagnosticos, PoliticaDeAcceso politica,
                                     RepositorioMascotas mascotas, RepositorioEventosDeMascota eventos,
                                     RepositorioAccionesDeMascota acciones, ParametrosDeMascota parametros) {
        this.diagnosticos = diagnosticos;
        this.mascotas = mascotas;
        this.eventos = eventos;
        this.acciones = acciones;
        this.autorizador = new AutorizadorDePlantas(plantas, accesos, politica);
        this.parametros = parametros;
        this.animo = new MapaDeAnimo(parametros);
    }

    @Transactional(readOnly = true)
    @Override
    public VistaDeMascota consultar(UUID solicitante, UUID plantaId) {
        autorizador.paraLeer(solicitante, plantaId);
        Optional<EstadoDeMascota> estado = mascotas.de(plantaId);
        Optional<DiagnosticoDePlanta> ultimo = diagnosticos.ultimoDe(plantaId);
        MapaDeAnimo.Resultado comoSeVe = animo.de(ultimo.map(DiagnosticoDePlanta::diagnostico), estado);
        return new VistaDeMascota(
                plantaId,
                ultimo.isPresent(),
                estado.map(EstadoDeMascota::vitalidadEntera).orElse((int) Math.round(parametros.vitalidadInicial())),
                estado.map(EstadoDeMascota::desmayada).orElse(false),
                comoSeVe.animo(),
                comoSeVe.causa(),
                ultimo.map(DiagnosticoDePlanta::estado).orElse(null),
                estado.map(EstadoDeMascota::rachaDias).orElse(0),
                estado.map(EstadoDeMascota::rachaDesde).orElse(null),
                estado.map(EstadoDeMascota::actualizadaEn).orElse(null),
                eventos.de(plantaId, new Paginacion(0, EVENTOS_EN_LA_VISTA)).elementos(),
                acciones.pendienteDe(plantaId),
                estado.map(EstadoDeMascota::accesorio).orElse(Accesorio.NINGUNO));
    }

    @Transactional(readOnly = true)
    @Override
    public Pagina<EventoDeMascota> eventos(UUID solicitante, UUID plantaId, Paginacion paginacion) {
        autorizador.paraLeer(solicitante, plantaId);
        return eventos.de(plantaId, paginacion);
    }
}
