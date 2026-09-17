package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.puerto.CatalogoDeEspecies;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba unitaria (sección 11): con un doble de {@link CatalogoDeEspecies}
 * (funcional, sin Mockito) para confirmar que {@link ServicioCatalogo} solo
 * delega, sin tocar Spring Data ni la entidad JPA (RA-02, RA-04).
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
    }

    @Test
    void unCatalogoVacioDevuelveListaVacia() {
        CatalogoDeEspecies catalogoDoble = List::of;
        ServicioCatalogo servicio = new ServicioCatalogo(catalogoDoble);

        assertThat(servicio.listar()).isEmpty();
    }
}
