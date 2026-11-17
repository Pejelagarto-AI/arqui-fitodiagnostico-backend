package com.vivero.fitodiagnostico.adaptador.entrada.rest;

import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.RespuestaAccionDeMascota;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.RespuestaEventoDeMascota;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.RespuestaMascota;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.RespuestaPagina;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.SolicitudAccionDeMascota;
import com.vivero.fitodiagnostico.dominio.modelo.Paginacion;
import com.vivero.fitodiagnostico.mascota.modelo.EstadoDeAccion;
import com.vivero.fitodiagnostico.mascota.modelo.ResultadoDeAccion;
import com.vivero.fitodiagnostico.mascota.modelo.TipoDeAccion;
import com.vivero.fitodiagnostico.mascota.modelo.Accesorio;
import com.vivero.fitodiagnostico.mascota.puerto.entrada.ActuarSobreMascotaPort;
import com.vivero.fitodiagnostico.mascota.puerto.entrada.CambiarAccesorioPort;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.SolicitudAccesorioDeMascota;
import org.springframework.web.bind.annotation.PatchMapping;
import com.vivero.fitodiagnostico.mascota.puerto.entrada.ConsultarMascotaPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/**
 * La mascota de una planta (fase 9). Mismas reglas de acceso que la planta: dueño e invitados consultan; solo el
 * dueño actúa (invitado 403 OPERACION_NO_PERMITIDA); ajeno o inexistente 404 PLANTA_NO_ENCONTRADA.
 */
@RestController
@RequestMapping("/api/v1/plantas/{plantaId}/mascota")
public class MascotaController {

    private final ConsultarMascotaPort consulta;
    private final ActuarSobreMascotaPort acciones;
    private final CambiarAccesorioPort accesorios;

    public MascotaController(ConsultarMascotaPort consulta, ActuarSobreMascotaPort acciones,
                             CambiarAccesorioPort accesorios) {
        this.consulta = consulta;
        this.acciones = acciones;
        this.accesorios = accesorios;
    }

    /**
     * Fase 13 (D182): el dueño le pone un accesorio. 200 con la misma vista que el GET; 403 al invitado, 404 si no es su
     * planta (como el resto) y 400 con un valor desconocido. Con la planta sin fila de mascota la crea.
     */
    @PatchMapping
    public RespuestaMascota accesorio(Authentication autenticacion, @PathVariable UUID plantaId,
                                      @RequestBody SolicitudAccesorioDeMascota solicitud) {
        UUID solicitante = IdentidadDelSolicitante.id(autenticacion);
        accesorios.cambiar(solicitante, plantaId, accesorioDe(solicitud));
        return RespuestaMascota.desde(consulta.consultar(solicitante, plantaId));
    }

    private static Accesorio accesorioDe(SolicitudAccesorioDeMascota solicitud) {
        String valor = solicitud == null || solicitud.accesorio() == null ? "" : solicitud.accesorio().trim().toUpperCase(Locale.ROOT);
        try {
            return Accesorio.valueOf(valor);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("el campo 'accesorio' debe ser uno de " + Arrays.toString(Accesorio.values()));
        }
    }

    /** Vitalidad, ánimo, racha, los cinco últimos eventos y el "la regué" pendiente. Sin lecturas, 200 igual. */
    @GetMapping
    public RespuestaMascota consultar(Authentication autenticacion, @PathVariable UUID plantaId) {
        return RespuestaMascota.desde(consulta.consultar(IdentidadDelSolicitante.id(autenticacion), plantaId));
    }

    /** La línea de tiempo, del más reciente al más antiguo; {@code tamano} de 1 a 100 (por defecto 20). */
    @GetMapping("/eventos")
    public RespuestaPagina<RespuestaEventoDeMascota> eventos(
            Authentication autenticacion, @PathVariable UUID plantaId,
            @RequestParam(name = "pagina", defaultValue = "0") int pagina,
            @RequestParam(name = "tamano", defaultValue = "" + Paginacion.TAMANO_POR_DEFECTO) int tamano) {
        return RespuestaPagina.desde(
                consulta.eventos(IdentidadDelSolicitante.id(autenticacion), plantaId, new Paginacion(pagina, tamano)),
                RespuestaEventoDeMascota::desde);
    }

    /**
     * 201 con ACARICIAR (ya pasó), o 200 con la última caricia si llegó antes de que pasaran 10 s (D172: sin evento ni
     * mensaje). 202 con LA_REGUE pendiente (se resolverá después), o 201 si el sensor ya lo
     * había confirmado. Un tipo desconocido o ausente, 400 PARAMETRO_INVALIDO.
     */
    @PostMapping("/acciones")
    public ResponseEntity<RespuestaAccionDeMascota> actuar(Authentication autenticacion, @PathVariable UUID plantaId,
                                                           @RequestBody SolicitudAccionDeMascota solicitud) {
        ResultadoDeAccion resultado = acciones.actuar(IdentidadDelSolicitante.id(autenticacion), plantaId,
                tipoDe(solicitud));
        HttpStatus estado = resultado.repetida() ? HttpStatus.OK
                : resultado.estado() == EstadoDeAccion.PENDIENTE ? HttpStatus.ACCEPTED : HttpStatus.CREATED;
        return ResponseEntity.status(estado).body(RespuestaAccionDeMascota.desde(resultado));
    }

    private static TipoDeAccion tipoDe(SolicitudAccionDeMascota solicitud) {
        String tipo = solicitud == null || solicitud.tipo() == null ? "" : solicitud.tipo().trim().toUpperCase(Locale.ROOT);
        try {
            return TipoDeAccion.valueOf(tipo);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("el campo 'tipo' debe ser uno de " + Arrays.toString(TipoDeAccion.values()));
        }
    }
}
