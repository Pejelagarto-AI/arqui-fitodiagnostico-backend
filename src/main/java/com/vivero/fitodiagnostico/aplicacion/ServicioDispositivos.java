package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.TokenDeDispositivo;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.VincularDispositivoPort;
import com.vivero.fitodiagnostico.dominio.puerto.salida.EmisorTokenDispositivoPort;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioAccesos;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioPlantas;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAcceso;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Caso de uso: vincular un dispositivo a una planta. La regla de negocio "solo el dueño" es de
 * {@link PoliticaDeAcceso} (a través de {@link AutorizadorDePlantas}, igual que editar o eliminar la
 * planta); firmar el token es del puerto {@link EmisorTokenDispositivoPort}, así que este servicio no
 * sabe qué es un JWT. No se guarda nada: el dispositivo no es una entidad del sistema, es una
 * credencial con caducidad (límite documentado: no hay revocación individual).
 */
@Service
public class ServicioDispositivos implements VincularDispositivoPort {

    private final AutorizadorDePlantas autorizador;
    private final EmisorTokenDispositivoPort emisor;

    public ServicioDispositivos(RepositorioPlantas plantas, RepositorioAccesos accesos, PoliticaDeAcceso politica,
                                EmisorTokenDispositivoPort emisor) {
        this.autorizador = new AutorizadorDePlantas(plantas, accesos, politica);
        this.emisor = emisor;
    }

    @Override
    @Transactional(readOnly = true)
    public TokenDeDispositivo vincular(UUID solicitante, UUID plantaId, String dispositivoId) {
        Planta planta = autorizador.paraModificar(solicitante, plantaId);
        return emisor.emitir(dispositivoId, planta.id());
    }
}
