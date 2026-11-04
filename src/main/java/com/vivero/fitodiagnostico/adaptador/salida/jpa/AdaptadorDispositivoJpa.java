package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.dominio.modelo.Dispositivo;
import com.vivero.fitodiagnostico.dominio.modelo.Reto;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioDispositivos;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adaptador JPA del puerto {@link RepositorioDispositivos}. */
@Repository
public class AdaptadorDispositivoJpa implements RepositorioDispositivos {

    private final DispositivoJpaRepository jpa;

    public AdaptadorDispositivoJpa(DispositivoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void guardar(Dispositivo d) {
        jpa.saveAndFlush(new DispositivoEntity(d.serial(), d.secreto(), d.plantaId().orElse(null),
                d.reclamadoEn().orElse(null), d.ultimaCredencialEn().orElse(null),
                d.reto().map(Reto::valor).orElse(null), d.reto().map(Reto::expira).orElse(null), d.creadoEn(), d.registradoPor()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Dispositivo> porSerial(String serial) {
        return jpa.findById(serial).map(AdaptadorDispositivoJpa::aDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Dispositivo> porPlanta(UUID plantaId) {
        return jpa.findByPlantaIdOrderByCreadoEnAscSerialAsc(plantaId).stream()
                .map(AdaptadorDispositivoJpa::aDominio).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Dispositivo> todos() {
        return jpa.findAllByOrderByCreadoEnAscSerialAsc().stream().map(AdaptadorDispositivoJpa::aDominio).toList();
    }

    private static Dispositivo aDominio(DispositivoEntity e) {
        Reto reto = e.getReto() == null ? null : new Reto(e.getReto(), e.getRetoExpira());
        return new Dispositivo(e.getSerial(), e.getSecreto(), e.getPlantaId(), e.getReclamadoEn(),
                e.getUltimaCredencialEn(), reto, e.getCreadoEn(), e.getRegistradoPor());
    }
}
