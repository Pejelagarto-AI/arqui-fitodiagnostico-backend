package com.vivero.fitodiagnostico.salud;

import java.util.Collection;
import java.util.Map;

/** Puerto de entrada de las pantallas: la salud de varios dispositivos a la vez, con su frase ya armada. */
public interface ConsultarSaludPort {

    record Vista(SaludDelDispositivo salud, String mensaje) { }

    /** Solo trae los dispositivos que alguna vez dejaron noticia; los demás no aparecen en el mapa. */
    Map<String, Vista> deLosDispositivos(Collection<String> seriales);
}
