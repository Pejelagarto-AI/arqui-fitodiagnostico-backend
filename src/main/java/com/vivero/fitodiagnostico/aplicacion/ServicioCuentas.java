package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.puerto.entrada.RegistrarCuentaPort;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.IniciarSesionPort;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.ConsultarCuentaPort;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.GestionarCuentaPort;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.AsegurarAdministradorInicialPort;
import com.vivero.fitodiagnostico.dominio.excepcion.ContrasenaActualIncorrectaException;
import com.vivero.fitodiagnostico.dominio.excepcion.CredencialesInvalidasException;
import com.vivero.fitodiagnostico.dominio.excepcion.UltimoAdministradorException;
import com.vivero.fitodiagnostico.dominio.modelo.TokenDeSesion;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import com.vivero.fitodiagnostico.dominio.puerto.salida.CifradorDeContrasena;
import com.vivero.fitodiagnostico.dominio.puerto.salida.EmisorDeTokens;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioUsuarios;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAdministradores;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Casos de uso de la cuenta propia (RF-A1 a RF-A6) y alta del primer
 * administrador. Orquesta puertos y política; no conoce BCrypt, JWT ni JPA.
 * El "quién soy" llega ya resuelto como {@link UUID} desde el borde web: este
 * servicio no sabe qué es una sesión.
 *
 * <p>RF-A3 (cerrar sesión) no tiene caso de uso: la sesión es un token sin
 * estado en el servidor y el cliente lo descarta. Ver docs/corte2/IMPLEMENTACION.md.
 */
@Service
public class ServicioCuentas implements RegistrarCuentaPort, IniciarSesionPort, ConsultarCuentaPort, GestionarCuentaPort, AsegurarAdministradorInicialPort {

    private final RepositorioUsuarios usuarios;
    private final CifradorDeContrasena cifrador;
    private final PoliticaDeAdministradores politicaDeAdministradores;
    private final EmisorDeTokens emisorDeTokens;
    private final CascadaDeEliminacion cascada;

    public ServicioCuentas(RepositorioUsuarios usuarios, CifradorDeContrasena cifrador,
                           PoliticaDeAdministradores politicaDeAdministradores,
                           EmisorDeTokens emisorDeTokens, CascadaDeEliminacion cascada) {
        this.usuarios = usuarios;
        this.cifrador = cifrador;
        this.politicaDeAdministradores = politicaDeAdministradores;
        this.emisorDeTokens = emisorDeTokens;
        this.cascada = cascada;
    }

    /** RF-A1. @throws com.vivero.fitodiagnostico.dominio.excepcion.CorreoYaRegistradoException RN1 */
    @Transactional
    @Override
    public Usuario registrar(String nombre, String correo, String contrasena) {
        String correoNormalizado = Usuario.normalizarCorreo(correo);
        OperacionesDeUsuario.exigirCorreoLibre(usuarios, correoNormalizado, null);
        Usuario nuevo = Usuario.nuevo(nombre, correoNormalizado, cifrador.cifrar(contrasena));
        usuarios.guardar(nuevo);
        return nuevo;
    }

    /** RF-A2. Un correo mal formado, uno inexistente y una contraseña errónea dan el mismo error. */
    @Transactional(readOnly = true)
    @Override
    public Sesion iniciarSesion(String correo, String contrasena) {
        String correoNormalizado;
        try {
            correoNormalizado = Usuario.normalizarCorreo(correo);
        } catch (IllegalArgumentException e) {
            throw new CredencialesInvalidasException();
        }
        Usuario usuario = usuarios.buscarPorCorreo(correoNormalizado)
                .filter(candidato -> cifrador.coincide(contrasena, candidato.credencial()))
                .orElseThrow(CredencialesInvalidasException::new);
        return new Sesion(usuario, emisorDeTokens.emitir(usuario));
    }

    /** RF-A4 (consultar). */
    @Transactional(readOnly = true)
    @Override
    public Usuario consultar(UUID id) {
        return OperacionesDeUsuario.cargar(usuarios, id);
    }

    /** RF-A4 (editar nombre y correo). */
    @Transactional
    @Override
    public Usuario actualizarDatos(UUID id, String nombre, String correo) {
        return OperacionesDeUsuario.editarDatos(usuarios, id, nombre, correo);
    }

    /** RF-A5: exige la contraseña actual. */
    @Transactional
    @Override
    public void cambiarContrasena(UUID id, String contrasenaActual, String contrasenaNueva) {
        Usuario usuario = OperacionesDeUsuario.cargar(usuarios, id);
        if (!cifrador.coincide(contrasenaActual, usuario.credencial())) {
            throw new ContrasenaActualIncorrectaException();
        }
        usuarios.guardar(usuario.conCredencial(cifrador.cifrar(contrasenaNueva)));
    }

    /**
     * RF-A6. Se lleva consigo sus plantas con la cascada de la RN12.
     *
     * @throws UltimoAdministradorException RN4, si es el único administrador
     */
    @Transactional
    @Override
    public void eliminar(UUID id) {
        Usuario usuario = OperacionesDeUsuario.cargar(usuarios, id);
        if (!politicaDeAdministradores.puedeDejarDeSerAdministrador(usuario, usuarios.contarAdministradores())) {
            throw new UltimoAdministradorException();
        }
        cascada.eliminarLoDe(id);
        usuarios.eliminar(id);
    }

    /**
     * Datos semilla: crea el primer administrador si el sistema no tiene
     * ninguno. Si ya hay uno no hace nada, así que es seguro correrlo en
     * cada arranque. Si el correo ya pertenece a un cliente, lo asciende
     * conservando su contraseña: quien controla el entorno de arranque es de
     * confianza, y la alternativa sería dejar el sistema sin administrador.
     *
     * @return {@code true} si creó o ascendió a alguien
     * @throws IllegalStateException si no hay administrador y faltan correo o contraseña: el
     *         sistema arrancaría sin nadie que pueda administrarlo, así que se prefiere no arrancar
     */
    @Transactional
    @Override
    public boolean asegurarAdministradorInicial(String correo, String contrasena) {
        if (usuarios.contarAdministradores() > 0) {
            return false;
        }
        if (correo == null || correo.isBlank() || contrasena == null || contrasena.isBlank()) {
            throw new IllegalStateException("no hay ningún administrador: define FITO_ADMIN_CORREO "
                    + "y FITO_ADMIN_CONTRASENA para crear el primero");
        }
        String correoNormalizado = Usuario.normalizarCorreo(correo);
        Usuario administrador = usuarios.buscarPorCorreo(correoNormalizado)
                .map(Usuario::conRolAdministrador)
                .orElseGet(() -> Usuario.nuevo("Administrador", correoNormalizado, cifrador.cifrar(contrasena))
                        .conRolAdministrador());
        usuarios.guardar(administrador);
        return true;
    }
}
