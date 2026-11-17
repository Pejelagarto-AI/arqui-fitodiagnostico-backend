package com.vivero.fitodiagnostico.adaptador.entrada.rest;

import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.RespuestaPlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.mascota.modelo.ResumenDeMascota;
import com.vivero.fitodiagnostico.mascota.puerto.entrada.ConsultarResumenDeMascotasPort;
import com.vivero.fitodiagnostico.salud.ConsultarSaludDePlantasPort;
import com.vivero.fitodiagnostico.salud.ConsultarSaludPort;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Arma el listado de plantas con su mascota y su salud (fase 13, D185). Compone dos puertos de consulta por lote (uno
 * por contexto) en el adaptador REST: ni la aplicación del diagnóstico conoce la mascota, ni la mascota la salud.
 * Una consulta de mascotas y otra de saludes por listado, no una por tarjeta.
 */
final class TableroDePlantas {

    private TableroDePlantas() { }

    static List<RespuestaPlanta> armar(List<Planta> plantas, UUID solicitante, ConsultarResumenDeMascotasPort mascotas,
                                       ConsultarSaludDePlantasPort saludes) {
        List<UUID> ids = plantas.stream().map(Planta::id).toList();
        Map<UUID, ResumenDeMascota> deMascotas = mascotas.deLasPlantas(ids);
        Map<UUID, ConsultarSaludPort.Vista> deSalud = saludes.laPeorDeLasPlantas(ids);
        return plantas.stream()
                .map(p -> RespuestaPlanta.desde(p, solicitante, deMascotas.get(p.id()), deSalud.get(p.id())))
                .toList();
    }
}
