package com.vivero.fitodiagnostico.dobles;

import com.vivero.fitodiagnostico.dominio.modelo.Aviso;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import com.vivero.fitodiagnostico.dominio.puerto.salida.Notificador;

import java.util.ArrayList;
import java.util.List;

/** Doble de {@link Notificador} que recuerda los avisos para poder afirmar qué se avisó y a quién. */
public class NotificadorGrabador implements Notificador {

    private final List<Aviso> avisos = new ArrayList<>();

    @Override
    public void avisar(Aviso aviso) {
        avisos.add(aviso);
    }

    public List<Aviso> avisos() {
        return List.copyOf(avisos);
    }

    public List<Aviso> avisosDeTipo(TipoDeAviso tipo) {
        return avisos.stream().filter(a -> a.tipo() == tipo).toList();
    }
}
