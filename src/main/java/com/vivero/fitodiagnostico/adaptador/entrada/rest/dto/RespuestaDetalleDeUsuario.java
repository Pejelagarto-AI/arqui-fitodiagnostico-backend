package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import com.vivero.fitodiagnostico.dominio.puerto.entrada.AdministrarUsuariosPort.DetalleDeUsuario;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * RF-B2: el usuario (misma forma que {@link RespuestaUsuario}, sin credencial)
 * más un resumen de sus plantas. Resumen, no detalle: el administrador sabe qué
 * plantas tiene el usuario, no lee sus diagnósticos ni sus invitados (RN3).
 */
public record RespuestaDetalleDeUsuario(UUID id, String nombre, String correo, List<String> roles,
                                        List<PlantaResumen> plantas) {

    public record PlantaResumen(UUID id, String apodo, String especie, Instant registradaEn) { }

    public static RespuestaDetalleDeUsuario desde(DetalleDeUsuario detalle) {
        RespuestaUsuario usuario = RespuestaUsuario.desde(detalle.usuario());
        return new RespuestaDetalleDeUsuario(usuario.id(), usuario.nombre(), usuario.correo(), usuario.roles(),
                detalle.plantas().stream().map(RespuestaDetalleDeUsuario::resumen).toList());
    }

    private static PlantaResumen resumen(Planta planta) {
        return new PlantaResumen(planta.id(), planta.apodo(), planta.especie().nombre(), planta.registradaEn());
    }
}
