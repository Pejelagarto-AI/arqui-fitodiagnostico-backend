package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.puerto.entrada.RecuperarContrasenaPort;
import com.vivero.fitodiagnostico.dominio.excepcion.RestablecimientoNoVigenteException;
import com.vivero.fitodiagnostico.dominio.modelo.Aviso;
import com.vivero.fitodiagnostico.dominio.modelo.SolicitudRestablecimiento;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import com.vivero.fitodiagnostico.dominio.puerto.salida.CifradorDeContrasena;
import com.vivero.fitodiagnostico.dominio.puerto.salida.GeneradorDeTokens;
import com.vivero.fitodiagnostico.dominio.puerto.salida.Notificador;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioRestablecimientos;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioUsuarios;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Recuperación de contraseña (RF-A7, RF-A8, RN14, D13). Sin sesión: quien
 * olvidó su contraseña no puede iniciarla, así que el enlace llega por correo.
 *
 * <p>{@link #solicitar} responde IGUAL exista o no el correo (D52): nada de lo
 * que devuelve ni lanza distingue los dos casos, así que el endpoint no sirve
 * para averiguar quién tiene cuenta. Lo único distinto es que, si existe, se
 * guarda la solicitud y se avisa.
 */
@Service
public class ServicioRecuperacion implements RecuperarContrasenaPort {

    private final RepositorioUsuarios usuarios;
    private final RepositorioRestablecimientos restablecimientos;
    private final CifradorDeContrasena cifrador;
    private final GeneradorDeTokens generador;
    private final Notificador notificador;
    private final Clock reloj;

    public ServicioRecuperacion(RepositorioUsuarios usuarios, RepositorioRestablecimientos restablecimientos,
                                CifradorDeContrasena cifrador, GeneradorDeTokens generador,
                                Notificador notificador, Clock reloj) {
        this.usuarios = usuarios;
        this.restablecimientos = restablecimientos;
        this.cifrador = cifrador;
        this.generador = generador;
        this.notificador = notificador;
        this.reloj = reloj;
    }

    /** RF-A7. No devuelve nada a propósito: ni siquiera un booleano "se envió". */
    @Transactional
    @Override
    public void solicitar(String correo) {
        Optional<Usuario> usuario = buscar(correo);
        if (usuario.isEmpty()) {
            return;
        }
        String token = generador.generar();
        restablecimientos.guardar(SolicitudRestablecimiento.nueva(usuario.get().id(),
                generador.huella(token), huellaDeLaCredencial(usuario.get()), reloj.instant()));
        notificador.avisar(new Aviso(Set.of(usuario.get().id()), TipoDeAviso.RESTABLECIMIENTO,
                "Pediste restablecer tu contraseña de Fitodiagnóstico. Si no fuiste tú, ignora este mensaje.",
                Map.of(Aviso.TOKEN, token)));
    }

    /**
     * RF-A8. Un solo uso (RN14): el enlace queda consumido al cambiar la contraseña.
     *
     * @throws RestablecimientoNoVigenteException si el token no existe, ya se usó, caducó, o la
     *         contraseña cambió desde que se pidió
     */
    @Transactional
    @Override
    public void restablecer(String token, String contrasenaNueva) {
        if (token == null || token.isBlank()) {
            throw new RestablecimientoNoVigenteException();
        }
        Instant ahora = reloj.instant();
        SolicitudRestablecimiento solicitud = restablecimientos.porHuella(generador.huella(token.trim()))
                .orElseThrow(RestablecimientoNoVigenteException::new);
        Usuario usuario = usuarios.buscarPorId(solicitud.usuarioId())
                .orElseThrow(RestablecimientoNoVigenteException::new);

        SolicitudRestablecimiento consumida = solicitud.consumida(ahora, huellaDeLaCredencial(usuario));
        usuarios.guardar(usuario.conCredencial(cifrador.cifrar(contrasenaNueva)));
        restablecimientos.guardar(consumida);
    }

    private Optional<Usuario> buscar(String correo) {
        try {
            return usuarios.buscarPorCorreo(Usuario.normalizarCorreo(correo));
        } catch (IllegalArgumentException e) {
            return Optional.empty();   // un correo sin forma de dirección tampoco existe
        }
    }

    /** Huella del hash vigente: cambia si y solo si la contraseña cambia (D52). */
    private String huellaDeLaCredencial(Usuario usuario) {
        return generador.huella(usuario.credencial().hash());
    }
}
