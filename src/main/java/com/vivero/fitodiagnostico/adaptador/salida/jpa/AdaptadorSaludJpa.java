package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.salud.RepositorioDeSalud;
import com.vivero.fitodiagnostico.salud.SaludDelDispositivo;
import com.vivero.fitodiagnostico.salud.SensorDeMatera;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Adaptador JPA del puerto {@link RepositorioDeSalud}. */
@Repository
public class AdaptadorSaludJpa implements RepositorioDeSalud {

    private final DispositivoSaludJpaRepository jpa;

    public AdaptadorSaludJpa(DispositivoSaludJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void guardar(SaludDelDispositivo s) {
        jpa.saveAndFlush(new DispositivoSaludEntity(s.serial(), s.estado(), s.sensores().get(SensorDeMatera.HUMEDAD),
                s.sensores().get(SensorDeMatera.LUZ), s.sensores().get(SensorDeMatera.TEMPERATURA), s.detalle(),
                s.reportadoEn(), s.ultimaLecturaEn(), s.cambioEn()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SaludDelDispositivo> de(String serial) {
        return jpa.findById(serial).map(AdaptadorSaludJpa::aDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaludDelDispositivo> de(Collection<String> seriales) {
        if (seriales.isEmpty()) {
            return List.of();
        }
        return jpa.findAllById(seriales).stream().map(AdaptadorSaludJpa::aDominio).toList();
    }

    private static SaludDelDispositivo aDominio(DispositivoSaludEntity e) {
        return new SaludDelDispositivo(e.getSerial(), e.getEstado(), Map.of(SensorDeMatera.HUMEDAD, e.getHumedad(),
                SensorDeMatera.LUZ, e.getLuz(), SensorDeMatera.TEMPERATURA, e.getTemperatura()), e.getDetalle(),
                e.getReportadoEn(), e.getUltimaLecturaEn(), e.getCambioEn());
    }
}
