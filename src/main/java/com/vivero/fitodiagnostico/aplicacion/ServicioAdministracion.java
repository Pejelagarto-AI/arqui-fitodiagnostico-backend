package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.excepcion.EspecieNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.excepcion.EspecieYaExisteException;
import com.vivero.fitodiagnostico.dominio.excepcion.UltimoAdministradorException;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import com.vivero.fitodiagnostico.dominio.puerto.CatalogoEditable;
import com.vivero.fitodiagnostico.dominio.puerto.RangosPorEspecie;
import com.vivero.fitodiagnostico.dominio.puerto.RepositorioUsuarios;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAdministradores;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Casos de uso del administrador: usuarios y roles (RF-B1 a RF-B5) y catálogo
 * de especies (RF-C3 a RF-C6). Que quien llama sea administrador lo garantiza
 * el borde web (la ruta exige el rol); este servicio no repite esa comprobación,
 * pero sí hace cumplir las reglas de negocio que dependen del estado: la RN4
 * (D18) y que una especie no se cree dos veces.
 *
 * <p>Las operaciones de lectura del catálogo para administración (RF-C2) no
 * están aquí sino en {@link ServicioCatalogo#listarTodas()}: es una consulta del
 * catálogo, no una modificación.
 */
@Service
public class ServicioAdministracion {

    private final RepositorioUsuarios usuarios;
    private final PoliticaDeAdministradores politicaDeAdministradores;
    private final CatalogoEditable catalogoEditable;
    private final RangosPorEspecie rangosPorEspecie;

    public ServicioAdministracion(RepositorioUsuarios usuarios,
                                  PoliticaDeAdministradores politicaDeAdministradores,
                                  CatalogoEditable catalogoEditable,
                                  RangosPorEspecie rangosPorEspecie) {
        this.usuarios = usuarios;
        this.politicaDeAdministradores = politicaDeAdministradores;
        this.catalogoEditable = catalogoEditable;
        this.rangosPorEspecie = rangosPorEspecie;
    }

    // ---- usuarios (módulo B) ----

    /** RF-B1: sin texto lista todos; con texto, los que lo contienen en nombre o correo. */
    @Transactional(readOnly = true)
    public List<Usuario> listarUsuarios(String texto) {
        return texto == null || texto.isBlank() ? usuarios.listar() : usuarios.buscar(texto.trim());
    }

    /**
     * RF-B2 (parte de usuario). Las plantas de las que es dueño se agregan
     * cuando exista el módulo de plantas; este método no cambia de firma por eso,
     * se le sumará una consulta al repositorio de plantas.
     */
    @Transactional(readOnly = true)
    public Usuario detalleDeUsuario(UUID id) {
        return OperacionesDeUsuario.cargar(usuarios, id);
    }

    /** RF-B3. */
    @Transactional
    public Usuario editarUsuario(UUID id, String nombre, String correo) {
        return OperacionesDeUsuario.editarDatos(usuarios, id, nombre, correo);
    }

    /** RF-B4. @throws UltimoAdministradorException RN4 */
    @Transactional
    public void eliminarUsuario(UUID id) {
        Usuario usuario = OperacionesDeUsuario.cargar(usuarios, id);
        exigirQuePuedaDejarDeSerAdministrador(usuario);
        usuarios.eliminar(id);
    }

    /** RF-B5 (otorgar). Idempotente. */
    @Transactional
    public Usuario otorgarAdministrador(UUID id) {
        Usuario promovido = OperacionesDeUsuario.cargar(usuarios, id).conRolAdministrador();
        usuarios.guardar(promovido);
        return promovido;
    }

    /** RF-B5 (revocar). Idempotente. @throws UltimoAdministradorException RN4 */
    @Transactional
    public Usuario revocarAdministrador(UUID id) {
        Usuario actual = OperacionesDeUsuario.cargar(usuarios, id);
        exigirQuePuedaDejarDeSerAdministrador(actual);
        Usuario degradado = actual.sinRolAdministrador();
        usuarios.guardar(degradado);
        return degradado;
    }

    private void exigirQuePuedaDejarDeSerAdministrador(Usuario usuario) {
        if (!politicaDeAdministradores.puedeDejarDeSerAdministrador(usuario, usuarios.contarAdministradores())) {
            throw new UltimoAdministradorException();
        }
    }

    // ---- catálogo de especies (módulo C) ----

    /** RF-C3. La especie nace activa. @throws EspecieYaExisteException si el nombre ya existe, activa o no */
    @Transactional
    public Especie crearEspecie(String nombre, Map<Magnitud, Rango> rangos) {
        exigirRangosCompletosYFisicos(rangos);
        if (rangosPorEspecie.buscar(nombre).isPresent()) {
            throw new EspecieYaExisteException(nombre);
        }
        Especie nueva = new Especie(nombre, rangos);
        catalogoEditable.guardar(nueva);
        return nueva;
    }

    /** RF-C4: cambia los rangos; el nombre es la clave y no se edita, y el estado de activación se conserva. */
    @Transactional
    public Especie editarEspecie(String nombre, Map<Magnitud, Rango> rangos) {
        exigirRangosCompletosYFisicos(rangos);
        Especie editada = existente(nombre).conRangos(rangos);
        catalogoEditable.guardar(editada);
        return editada;
    }

    /** RF-C5. Idempotente. No elimina: deja de ofrecerse, sigue diagnosticándose (RN11). */
    @Transactional
    public Especie desactivarEspecie(String nombre) {
        Especie descontinuada = existente(nombre).desactivada();
        catalogoEditable.cambiarActivacion(descontinuada.nombre(), false);
        return descontinuada;
    }

    /** RF-C6. Idempotente. */
    @Transactional
    public Especie reactivarEspecie(String nombre) {
        Especie reactivada = existente(nombre).reactivada();
        catalogoEditable.cambiarActivacion(reactivada.nombre(), true);
        return reactivada;
    }

    private Especie existente(String nombre) {
        return rangosPorEspecie.buscar(nombre).orElseThrow(() -> new EspecieNoEncontradaException(nombre));
    }

    /**
     * RF-C3 pide "su rango óptimo para cada magnitud", e {@code Especie} por sí
     * sola solo exige uno (los tests del núcleo construyen especies parciales).
     * Aquí se exige el conjunto completo y que cada rango óptimo quepa en lo
     * que el sensor puede medir: un óptimo de humedad hasta 150 % nunca se
     * cumpliría ni se incumpliría.
     */
    private static void exigirRangosCompletosYFisicos(Map<Magnitud, Rango> rangos) {
        if (rangos == null) {
            throw new IllegalArgumentException("la especie requiere un rango para cada magnitud");
        }
        for (Magnitud magnitud : Magnitud.values()) {
            Rango rango = rangos.get(magnitud);
            if (rango == null) {
                throw new IllegalArgumentException("falta el rango de " + magnitud.descripcion());
            }
            Rango fisico = magnitud.rangoFisico();
            if (rango.minimo() < fisico.minimo() || rango.maximo() > fisico.maximo()) {
                throw new IllegalArgumentException("el rango de " + magnitud.descripcion()
                        + " debe estar entre " + fisico.minimo() + " y " + fisico.maximo() + " " + magnitud.unidad());
            }
        }
    }
}
