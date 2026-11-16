package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import com.vivero.fitodiagnostico.salud.ConsultarSaludPort;
import com.vivero.fitodiagnostico.salud.SaludDelDispositivo;
import com.vivero.fitodiagnostico.salud.SensorDeMatera;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * La salud de una matera (fase 10). {@code estado}: OK, FALLA o SIN_NOTICIAS. {@code sensores}: humedad, luz y
 * temperatura, cada uno OK o FALLA. {@code mensaje}: la frase en primera persona lista para mostrar, calculada al
 * pedir (D158). {@code ultimaNoticiaEn}: lo último que se supo, lectura o reporte; con él la app puede seguir
 * contando el "hace X min" sin volver a pedir.
 */
public record RespuestaSalud(String estado, Map<String, String> sensores, String detalle, Instant reportadoEn,
                             Instant cambioEn, Instant ultimaNoticiaEn, String mensaje) {

    public static RespuestaSalud desde(ConsultarSaludPort.Vista vista) {
        if (vista == null) {
            return null;
        }
        SaludDelDispositivo s = vista.salud();
        Map<String, String> sensores = new LinkedHashMap<>();
        for (SensorDeMatera sensor : SensorDeMatera.values()) {
            sensores.put(sensor.clave(), s.sensores().get(sensor).name());
        }
        return new RespuestaSalud(s.estado().name(), sensores, s.detalle(), s.reportadoEn(), s.cambioEn(),
                s.ultimaNoticiaEn().orElse(null), vista.mensaje());
    }
}
