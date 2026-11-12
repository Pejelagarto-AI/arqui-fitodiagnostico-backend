package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.puerto.salida.CatalogoDeEspecies;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba unitaria (sección 11): con un doble de {@link CatalogoDeEspecies}
 * (funcional, sin Mockito) para confirmar que {@link ServicioCatalogo} delega
 * y filtra las inactivas solo en el listado público (D8), sin tocar Spring Data ni la entidad JPA (RA-02, RA-04).
 */
class ServicioCatalogoTest {

    private static Especie especie(String nombre) {
        Map<Magnitud, Rango> rangos = new EnumMap<>(Magnitud.class);
        rangos.put(Magnitud.HUMEDAD, new Rango(20.0, 45.0));
        rangos.put(Magnitud.LUZ, new Rango(200, 1500));
        rangos.put(Magnitud.TEMPERATURA, new Rango(15.0, 29.0));
        return new Especie(nombre, rangos);
    }

    @Test
    void delegaElListadoEnElCatalogoDeEspecies() {
        List<Especie> especies = List.of(especie("helecho"), especie("lavanda"));
        CatalogoDeEspecies catalogoDoble = () -> especies;
        ServicioCatalogo servicio = new ServicioCatalogo(catalogoDoble);

        assertThat(servicio.listar()).isEqualTo(especies);
        assertThat(servicio.listarTodas()).isEqualTo(especies);
    }

    @Test
    void elListadoPublicoFiltraLasEspeciesInactivas() {
        Especie activa = especie("helecho");
        Especie descontinuada = especie("lavanda").desactivada();
        ServicioCatalogo servicio = new ServicioCatalogo(() -> List.of(activa, descontinuada));

        assertThat(servicio.listar()).containsExactly(activa);
    }

    @Test
    void elListadoDeAdministracionIncluyeLasInactivas() {
        Especie activa = especie("helecho");
        Especie descontinuada = especie("lavanda").desactivada();
        ServicioCatalogo servicio = new ServicioCatalogo(() -> List.of(activa, descontinuada));

        assertThat(servicio.listarTodas()).containsExactly(activa, descontinuada);
    }

    @Test
    void unCatalogoVacioDevuelveListaVacia() {
        CatalogoDeEspecies catalogoDoble = List::of;
        ServicioCatalogo servicio = new ServicioCatalogo(catalogoDoble);

        assertThat(servicio.listar()).isEmpty();
    }
}
