package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.excepcion.OperacionNoPermitidaException;
import com.vivero.fitodiagnostico.dominio.excepcion.PlantaNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.puerto.RepositorioAccesos;
import com.vivero.fitodiagnostico.dominio.puerto.RepositorioPlantas;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAcceso;

import java.util.UUID;

/**
 * El paso "cargar la planta y comprobar que este usuario puede" que repiten
 * {@link ServicioPlantas}, {@link ServicioMediciones} y
 * {@link ServicioConsultaDeDiagnosticos}. Junta lo que {@link PoliticaDeAcceso}
 * no hace por diseño (D19): traer los invitados del puerto y dárselos ya
 * cargados. Sin estado ni Spring; cada servicio lo arma con sus puertos.
 *
 * <p>Decisión de existencia: quien no puede LEER la planta recibe
 * {@link PlantaNoEncontradaException}, igual que si no existiera (RN3, D35).
 * Quien puede leer pero no modificar (invitado) recibe
 * {@link OperacionNoPermitidaException}: ya sabe que la planta existe, así
 * que un 403 no filtra nada y explica mejor qué pasó.
 */
final class AutorizadorDePlantas {

    private final RepositorioPlantas plantas;
    private final RepositorioAccesos accesos;
    private final PoliticaDeAcceso politica;

    AutorizadorDePlantas(RepositorioPlantas plantas, RepositorioAccesos accesos, PoliticaDeAcceso politica) {
        this.plantas = plantas;
        this.accesos = accesos;
        this.politica = politica;
    }

    /** @throws PlantaNoEncontradaException si no existe o el usuario no es dueño ni invitado */
    Planta paraLeer(UUID usuario, UUID plantaId) {
        Planta planta = plantas.buscar(plantaId).orElseThrow(() -> new PlantaNoEncontradaException(plantaId));
        if (!politica.puedeLeer(usuario, planta, accesos.invitadosDe(plantaId))) {
            throw new PlantaNoEncontradaException(plantaId);
        }
        return planta;
    }

    /**
     * @throws PlantaNoEncontradaException si no puede ni leerla
     * @throws OperacionNoPermitidaException si puede leerla pero no es el dueño (RN5)
     */
    Planta paraModificar(UUID usuario, UUID plantaId) {
        Planta planta = paraLeer(usuario, plantaId);
        if (!politica.puedeModificar(usuario, planta)) {
            throw new OperacionNoPermitidaException("solo el dueño de la planta puede hacer esta operación");
        }
        return planta;
    }
}
