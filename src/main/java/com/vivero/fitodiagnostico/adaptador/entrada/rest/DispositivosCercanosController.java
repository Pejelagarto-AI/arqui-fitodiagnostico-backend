package com.vivero.fitodiagnostico.adaptador.entrada.rest;

import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.RespuestaDispositivoCercano;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.DescubrirDispositivosPort;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Adaptador de entrada de la PERSONA que está vinculando una matera: "¿cuáles hay cerca?". Ruta de usuario
 * autenticado (cae en {@code anyRequest}: un token de dispositivo recibe 403, sin sesión 401). La dirección de
 * quien pregunta es la del socket ({@code getRemoteAddr}); detrás de un proxy inverso habría que confiar en
 * {@code X-Forwarded-For}, y no se hace aquí porque cualquiera podría falsificarla (D94).
 */
@RestController
@RequestMapping("/api/v1/dispositivos/cercanos")
public class DispositivosCercanosController {

    private final DescubrirDispositivosPort descubrimiento;

    public DispositivosCercanosController(DescubrirDispositivosPort descubrimiento) {
        this.descubrimiento = descubrimiento;
    }

    @GetMapping
    public List<RespuestaDispositivoCercano> cercanos(Authentication autenticacion, HttpServletRequest peticion) {
        return descubrimiento.cercanos(IdentidadDelSolicitante.id(autenticacion), peticion.getRemoteAddr()).stream()
                .map(RespuestaDispositivoCercano::desde).toList();
    }
}
