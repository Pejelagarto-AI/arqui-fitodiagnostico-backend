package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.puerto.entrada.CompartirPlantaPort;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.AceptarInvitacionPort;
import com.vivero.fitodiagnostico.dominio.excepcion.InvitacionNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.excepcion.InvitadoNoEncontradoException;
import com.vivero.fitodiagnostico.dominio.excepcion.OperacionNoPermitidaException;
import com.vivero.fitodiagnostico.dominio.excepcion.PlantaNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.modelo.AccesoCompartido;
import com.vivero.fitodiagnostico.dominio.modelo.Aviso;
import com.vivero.fitodiagnostico.dominio.modelo.Invitacion;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import com.vivero.fitodiagnostico.dominio.puerto.salida.GeneradorDeTokens;
import com.vivero.fitodiagnostico.dominio.puerto.salida.Notificador;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioAccesos;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioInvitaciones;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioPlantas;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioUsuarios;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAcceso;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Compartir plantas (RF-E1 a RF-E6, RN13, D12): el dueño genera un enlace de
 * un solo uso, quien lo recibe lo acepta (con sesión, o registrándose en el
 * mismo paso), y el dueño o el propio invitado pueden cortar el acceso.
 *
 * <p>Qué es del dueño y qué no lo decide {@link AutorizadorDePlantas}, como en
 * los demás servicios de plantas (D36): un invitado que intenta generar o
 * revocar recibe 403 y un ajeno, 404. Las decisiones que dependen de la hora
 * usan el {@link Clock} inyectado, no el reloj del sistema, para poder probar
 * la caducidad con fechas fijas.
 *
 * <p>El token en claro existe UNA vez: en el valor que devuelve
 * {@link #generar}. Lo que se guarda y se busca es su huella (D51).
 */
@Service
public class ServicioInvitaciones implements CompartirPlantaPort, AceptarInvitacionPort {

    private final RepositorioPlantas plantas;
    private final RepositorioAccesos accesos;
    private final RepositorioInvitaciones invitaciones;
    private final RepositorioUsuarios usuarios;
    private final GeneradorDeTokens generador;
    private final Notificador notificador;
    private final ServicioCuentas cuentas;
    private final Clock reloj;
    private final AutorizadorDePlantas autorizador;

    public ServicioInvitaciones(RepositorioPlantas plantas, RepositorioAccesos accesos,
                                RepositorioInvitaciones invitaciones, RepositorioUsuarios usuarios,
                                PoliticaDeAcceso politica, GeneradorDeTokens generador,
                                Notificador notificador, ServicioCuentas cuentas, Clock reloj) {
        this.plantas = plantas;
        this.accesos = accesos;
        this.invitaciones = invitaciones;
        this.usuarios = usuarios;
        this.generador = generador;
        this.notificador = notificador;
        this.cuentas = cuentas;
        this.reloj = reloj;
        this.autorizador = new AutorizadorDePlantas(plantas, accesos, politica);
    }

    // ---- lado del dueño ----

    /**
     * RF-E1. Solo el dueño. Con {@code correoOpcional} además le llega el
     * enlace por correo (aviso INVITACION); sin él, el dueño reparte el enlace.
     *
     * @throws OperacionNoPermitidaException si no es el dueño, o si el correo es el suyo
     * @throws IllegalArgumentException si el correo no tiene forma de dirección
     */
    @Transactional
    @Override
    public InvitacionGenerada generar(UUID solicitante, UUID plantaId, String correoOpcional) {
        Planta planta = autorizador.paraModificar(solicitante, plantaId);
        String correo = correoOpcional == null || correoOpcional.isBlank()
                ? null : Usuario.normalizarCorreo(correoOpcional);
        if (correo != null) {
            usuarios.buscarPorCorreo(correo)
                    .filter(destinatario -> destinatario.id().equals(planta.duenoId()))
                    .ifPresent(propio -> {
                        throw new OperacionNoPermitidaException("no puedes invitarte a ti mismo a tu propia planta");
                    });
        }

        String token = generador.generar();
        Invitacion invitacion = Invitacion.nueva(plantaId, generador.huella(token), reloj.instant(), correo);
        invitaciones.guardar(invitacion);

        if (correo != null) {
            String quien = usuarios.buscarPorId(solicitante).map(Usuario::nombre).orElse("Alguien");
            notificador.avisar(Aviso.aCorreos(Set.of(correo), TipoDeAviso.INVITACION,
                    quien + " te invitó a ver su planta «" + planta.apodo() + "» en Fitodiagnóstico.",
                    Map.of(Aviso.TOKEN, token)));
        }
        return new InvitacionGenerada(invitacion, token);
    }

    /** Las invitaciones de la planta que siguen vigentes (las caducadas no se listan). Solo el dueño. */
    @Transactional(readOnly = true)
    @Override
    public List<Invitacion> pendientes(UUID solicitante, UUID plantaId) {
        autorizador.paraModificar(solicitante, plantaId);
        Instant ahora = reloj.instant();
        return invitaciones.pendientesDe(plantaId).stream().filter(i -> i.estaVigenteEn(ahora)).toList();
    }

    /**
     * RF-E3. Solo el dueño, y solo una invitación aún pendiente: si ya se aceptó,
     * lo que corresponde es revocar el acceso (RF-E4).
     *
     * @throws InvitacionNoEncontradaException si no existe o es de otra planta
     * @throws com.vivero.fitodiagnostico.dominio.excepcion.InvitacionNoVigenteException si ya no estaba pendiente
     */
    @Transactional
    @Override
    public void revocar(UUID solicitante, UUID plantaId, UUID invitacionId) {
        autorizador.paraModificar(solicitante, plantaId);
        Invitacion invitacion = invitaciones.buscar(invitacionId)
                .filter(i -> i.plantaId().equals(plantaId))
                .orElseThrow(InvitacionNoEncontradaException::new);
        invitaciones.guardar(invitacion.revocada(reloj.instant()));
    }

    /** Quiénes tienen acceso a la planta y desde cuándo. Solo el dueño. */
    @Transactional(readOnly = true)
    @Override
    public List<Invitado> invitados(UUID solicitante, UUID plantaId) {
        autorizador.paraModificar(solicitante, plantaId);
        return accesos.accesosDe(plantaId).stream()
                .flatMap(acceso -> usuarios.buscarPorId(acceso.invitadoId())
                        .map(usuario -> new Invitado(usuario, acceso.otorgadoEn())).stream())
                .toList();
    }

    /**
     * RF-E4. Solo el dueño. El invitado recibe ACCESO_PERDIDO (RF-G3).
     *
     * @throws InvitadoNoEncontradoException si ese usuario no era invitado de la planta
     */
    @Transactional
    @Override
    public void revocarAcceso(UUID solicitante, UUID plantaId, UUID invitadoId) {
        Planta planta = autorizador.paraModificar(solicitante, plantaId);
        if (!accesos.invitadosDe(plantaId).contains(invitadoId)) {
            throw new InvitadoNoEncontradoException(invitadoId);
        }
        accesos.revocar(plantaId, invitadoId);
        notificador.avisar(new Aviso(Set.of(invitadoId), TipoDeAviso.ACCESO_PERDIDO,
                "Perdiste el acceso a la planta «" + planta.apodo() + "» porque su dueño te lo quitó.",
                Map.of(Aviso.PLANTA_ID, plantaId.toString())));
    }

    // ---- lado del invitado ----

    /**
     * RF-E2 con sesión. Consume la invitación y le da acceso de lectura a
     * quien la acepta. Todas las comprobaciones van ANTES de cualquier
     * escritura: una invitación rechazada no queda consumida.
     *
     * @throws InvitacionNoEncontradaException si el token no corresponde a ninguna invitación
     * @throws com.vivero.fitodiagnostico.dominio.excepcion.InvitacionNoVigenteException si ya se usó, se revocó o caducó (RN13)
     * @throws OperacionNoPermitidaException si quien acepta es el dueño de la planta
     */
    @Transactional
    @Override
    public Planta aceptar(UUID usuarioId, String token) {
        Instant ahora = reloj.instant();
        Invitacion invitacion = vigentePorToken(token, ahora);
        Planta planta = plantaDe(invitacion);
        exigirQueNoSeaElDueno(planta, usuarioId);
        consumir(invitacion, planta, usuarioId, ahora);
        return planta;
    }

    /**
     * RF-E2 sin cuenta: el visitante se registra y acepta en el mismo flujo.
     * La invitación se valida ANTES de crear la cuenta (un token inválido no
     * deja una cuenta nueva) y las dos escrituras viajan en una sola
     * transacción: si el correo ya está registrado, no se consume el enlace.
     *
     * @throws com.vivero.fitodiagnostico.dominio.excepcion.CorreoYaRegistradoException si el correo ya tiene cuenta (RN1)
     */
    @Transactional
    @Override
    public AceptacionConRegistro registrarYAceptar(String token, String nombre, String correo, String contrasena) {
        Instant ahora = reloj.instant();
        Invitacion invitacion = vigentePorToken(token, ahora);
        Planta planta = plantaDe(invitacion);
        Usuario nuevo = cuentas.registrar(nombre, correo, contrasena);
        consumir(invitacion, planta, nuevo.id(), ahora);
        return new AceptacionConRegistro(nuevo, planta);
    }

    /** RF-E5: las plantas que otros me compartieron, la más reciente primero. */
    @Transactional(readOnly = true)
    @Override
    public List<Planta> compartidasConmigo(UUID usuarioId) {
        return plantas.compartidasCon(usuarioId);
    }

    /**
     * RF-E6. No se avisa a nadie: el invitado lo hizo él mismo, y RF-G3 es
     * para quien PIERDE el acceso sin haberlo decidido (D61).
     *
     * @throws PlantaNoEncontradaException si la planta no existe o no le estaba compartida
     */
    @Transactional
    @Override
    public void renunciar(UUID usuarioId, UUID plantaId) {
        if (!accesos.invitadosDe(plantaId).contains(usuarioId)) {
            throw new PlantaNoEncontradaException(plantaId);
        }
        accesos.revocar(plantaId, usuarioId);
    }

    // ---- internos ----

    private Invitacion vigentePorToken(String token, Instant ahora) {
        if (token == null || token.isBlank()) {
            throw new InvitacionNoEncontradaException();
        }
        Invitacion invitacion = invitaciones.porHuella(generador.huella(token.trim()))
                .orElseThrow(InvitacionNoEncontradaException::new);
        invitacion.exigirVigente(ahora);
        return invitacion;
    }

    private Planta plantaDe(Invitacion invitacion) {
        // La cascada borra las invitaciones de una planta eliminada; si aun así faltara, es "no existe".
        return plantas.buscar(invitacion.plantaId()).orElseThrow(InvitacionNoEncontradaException::new);
    }

    private static void exigirQueNoSeaElDueno(Planta planta, UUID usuarioId) {
        if (planta.esDe(usuarioId)) {
            throw new OperacionNoPermitidaException("no puedes aceptar una invitación a tu propia planta");
        }
    }

    private void consumir(Invitacion invitacion, Planta planta, UUID invitadoId, Instant ahora) {
        accesos.otorgar(new AccesoCompartido(planta.id(), invitadoId, ahora));
        invitaciones.guardar(invitacion.aceptada(ahora));
    }
}
