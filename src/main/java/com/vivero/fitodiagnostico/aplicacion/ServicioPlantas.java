package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.excepcion.EspecieNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.puerto.RangosPorEspecie;
import com.vivero.fitodiagnostico.dominio.puerto.RepositorioAccesos;
import com.vivero.fitodiagnostico.dominio.puerto.RepositorioPlantas;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAcceso;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Casos de uso de las plantas (RF-D1 a RF-D5). El "quién soy" llega como
 * {@link UUID}; los permisos los decide {@link PoliticaDeAcceso} a través de
 * {@link AutorizadorDePlantas}, no este servicio. Que la especie sea activa
 * (RN11) lo decide la propia {@link Planta}; aquí solo se busca la especie.
 */
@Service
public class ServicioPlantas {

    private final RepositorioPlantas plantas;
    private final RangosPorEspecie especies;
    private final CascadaDeEliminacion cascada;
    private final AutorizadorDePlantas autorizador;

    public ServicioPlantas(RepositorioPlantas plantas, RepositorioAccesos accesos, PoliticaDeAcceso politica,
                           RangosPorEspecie especies, CascadaDeEliminacion cascada) {
        this.plantas = plantas;
        this.especies = especies;
        this.cascada = cascada;
        this.autorizador = new AutorizadorDePlantas(plantas, accesos, politica);
    }

    /**
     * RF-D1.
     *
     * @throws EspecieNoEncontradaException si la especie no existe
     * @throws com.vivero.fitodiagnostico.dominio.excepcion.EspecieInactivaException si está descontinuada (RN11)
     */
    @Transactional
    public Planta registrar(UUID duenoId, String apodo, String nombreDeEspecie) {
        Planta nueva = Planta.nueva(duenoId, apodo, buscarEspecie(nombreDeEspecie));
        plantas.guardar(nueva);
        return nueva;
    }

    /** RF-D2: solo las propias. Las compartidas conmigo son otro listado (RF-E5, bloque siguiente). */
    @Transactional(readOnly = true)
    public List<Planta> listarPropias(UUID duenoId) {
        return plantas.deDueno(duenoId);
    }

    /** RF-D3: dueño o invitado. */
    @Transactional(readOnly = true)
    public Planta detalle(UUID solicitante, UUID plantaId) {
        return autorizador.paraLeer(solicitante, plantaId);
    }

    /** RF-D4: solo el dueño. Cambiar a otra especie exige que esté activa (RN11); conservar la misma, no. */
    @Transactional
    public Planta editar(UUID solicitante, UUID plantaId, String apodo, String nombreDeEspecie) {
        Planta actual = autorizador.paraModificar(solicitante, plantaId);
        Planta editada = actual.conApodo(apodo).conEspecie(buscarEspecie(nombreDeEspecie));
        plantas.guardar(editada);
        return editada;
    }

    /** RF-D5: solo el dueño; con la cascada de la RN12 y el aviso a los invitados (RF-G3). */
    @Transactional
    public void eliminar(UUID solicitante, UUID plantaId) {
        cascada.eliminarPlanta(autorizador.paraModificar(solicitante, plantaId));
    }

    private Especie buscarEspecie(String nombre) {
        return especies.buscar(nombre).orElseThrow(() -> new EspecieNoEncontradaException(nombre));
    }
}
