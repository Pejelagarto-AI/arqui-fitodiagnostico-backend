package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dominio.estado.*;
import com.vivero.fitodiagnostico.dominio.modelo.Ambiente;
import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluadorDeEstadoTest {

    // Orden de prioridad tal como lo ensamblaría ConfiguracionDominio (sección 8).
    private final EvaluadorDeEstado evaluador = new EvaluadorDeEstado(List.of(
            new EstadoNecesitaAbrigo(),
            new EstadoNecesitaAgua(),
            new EstadoNecesitaLuz(),
            new EstadoOptimo()));

    // Monstera deliciosa: temp 18-29 °C, humedad 55-80 % HR, luz 1000-2500 lux
    private final Especie especie = new Especie(
            "Monstera deliciosa", "costilla de Adán",
            new Rango(18.0, 29.0), new Rango(55.0, 80.0), new Rango(1000, 2500));

    @Test
    void conLasTresLecturasDentroDeRangoDevuelveOptimo() {
        Ambiente ambiente = new Ambiente(20.0, 60.0, 1500);
        Diagnostico diagnostico = evaluador.evaluar(especie, ambiente);
        assertThat(diagnostico.estado()).isEqualTo("OPTIMO");
    }

    @Test
    void devuelveElPrimerEstadoQueAplicaCuandoSoloUnoFalla() {
        // Solo la humedad está fuera de rango: debe ganar NECESITA_AGUA.
        Ambiente ambiente = new Ambiente(20.0, 40.0, 1500);
        Diagnostico diagnostico = evaluador.evaluar(especie, ambiente);
        assertThat(diagnostico.estado()).isEqualTo("NECESITA_AGUA");
    }

    @Test
    void laTemperaturaFueraDeRangoTienePrioridadSobreLaHumedadYLaLuz() {
        // Las tres variables están fuera de rango a la vez: debe ganar la de
        // mayor prioridad en la lista, NECESITA_ABRIGO, no las otras dos.
        Ambiente ambiente = new Ambiente(5.0, 20.0, 200);
        Diagnostico diagnostico = evaluador.evaluar(especie, ambiente);
        assertThat(diagnostico.estado()).isEqualTo("NECESITA_ABRIGO");
    }

    @Test
    void laHumedadTienePrioridadSobreLaLuzCuandoAmbasFallan() {
        // Temperatura ok, humedad y luz fuera de rango: debe ganar NECESITA_AGUA
        // sobre NECESITA_LUZ porque así está ordenada la lista.
        Ambiente ambiente = new Ambiente(20.0, 40.0, 200);
        Diagnostico diagnostico = evaluador.evaluar(especie, ambiente);
        assertThat(diagnostico.estado()).isEqualTo("NECESITA_AGUA");
    }

    @Test
    void elDiagnosticoConservaLaEspecieYElAmbienteRecibidos() {
        Ambiente ambiente = new Ambiente(20.0, 60.0, 1500);
        Diagnostico diagnostico = evaluador.evaluar(especie, ambiente);
        assertThat(diagnostico.especie()).isEqualTo(especie);
        assertThat(diagnostico.ambiente()).isEqualTo(ambiente);
        assertThat(diagnostico.detalle()).isNotBlank();
        assertThat(diagnostico.evaluadoEn()).isNotNull();
    }

    @Test
    void siNingunEstadoAplicaLanzaIllegalStateException() {
        // Sin un estado terminal en la lista, la cadena debe fallar explícitamente.
        EvaluadorDeEstado evaluadorSinTerminal = new EvaluadorDeEstado(List.of(new EstadoNecesitaAgua()));
        Ambiente ambiente = new Ambiente(20.0, 60.0, 1500);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> evaluadorSinTerminal.evaluar(especie, ambiente));
    }
}
