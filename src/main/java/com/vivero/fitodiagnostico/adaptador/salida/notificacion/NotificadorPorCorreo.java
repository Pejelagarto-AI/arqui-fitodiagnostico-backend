package com.vivero.fitodiagnostico.adaptador.salida.notificacion;

import com.vivero.fitodiagnostico.dominio.modelo.Aviso;
import com.vivero.fitodiagnostico.dominio.modelo.Invitacion;
import com.vivero.fitodiagnostico.dominio.modelo.SolicitudRestablecimiento;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import com.vivero.fitodiagnostico.dominio.puerto.salida.Notificador;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioUsuarios;
import com.vivero.fitodiagnostico.adaptador.salida.notificacion.EnlacesDelFront;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Adaptador de {@link Notificador} por correo SMTP (D13, D54). Un correo por
 * destinatario, nunca con copia a varios: nadie debe ver las direcciones de los
 * demás invitados de una planta.
 *
 * <p><b>Dónde vive la resolución UUID a dirección (D54):</b> aquí, en el
 * adaptador, consultando el puerto {@link RepositorioUsuarios}. La dirección
 * es un dato del CANAL (el WebSocket no la necesita, el log no debe verla) y
 * el aviso lleva ids. Única excepción: {@link Aviso#correos()}, para quien
 * aún no tiene cuenta y por tanto no tiene id (la invitación).
 *
 * <p><b>Asíncrono por defecto (D54):</b> el envío sale en un hilo aparte. Un
 * SMTP lento o caído no retrasa ni rompe la petición que originó el aviso (la
 * medición ya quedó guardada), y, de paso, solicitar el restablecimiento tarda
 * lo mismo exista o no el correo. En pruebas se desactiva
 * ({@code fitodiagnostico.correo.asincrono=false}) para afirmar sin esperar.
 *
 * <p>Cumple el contrato del puerto: no lanza. Un fallo de envío se registra
 * (tipo y causa, sin direcciones ni token) y se sigue con el siguiente.
 */
@Component
public class NotificadorPorCorreo implements Notificador {

    private static final Logger log = LoggerFactory.getLogger(NotificadorPorCorreo.class);

    private final MailSender enviador;
    private final RepositorioUsuarios usuarios;
    private final EnlacesDelFront enlaces;
    private final String remitente;
    private final ExecutorService ejecutor;

    public NotificadorPorCorreo(MailSender enviador, RepositorioUsuarios usuarios, EnlacesDelFront enlaces,
                                @Value("${fitodiagnostico.correo.remitente:no-responder@fitodiagnostico.local}")
                                String remitente,
                                @Value("${fitodiagnostico.correo.asincrono:true}") boolean asincrono) {
        this.enviador = enviador;
        this.usuarios = usuarios;
        this.enlaces = enlaces;
        this.remitente = remitente;
        this.ejecutor = asincrono ? Executors.newFixedThreadPool(2, tarea -> {
            Thread hilo = new Thread(tarea, "correo-de-avisos");
            hilo.setDaemon(true);
            return hilo;
        }) : null;
    }

    @Override
    public void avisar(Aviso aviso) {
        if (ejecutor == null) {
            enviar(aviso);
        } else {
            try {
                ejecutor.execute(() -> enviar(aviso));
            } catch (RuntimeException e) {
                log.warn("no se pudo encolar el correo del aviso {}: {}", aviso.tipo(), e.getMessage());
            }
        }
    }

    @PreDestroy
    void cerrar() {
        if (ejecutor != null) {
            ejecutor.shutdown();
        }
    }

    private void enviar(Aviso aviso) {
        try {
            for (String direccion : direccionesDe(aviso)) {
                enviarA(direccion, aviso);
            }
        } catch (RuntimeException e) {
            log.warn("no se pudieron resolver los destinatarios del correo del aviso {}: {}",
                    aviso.tipo(), e.getMessage());
        }
    }

    private Set<String> direccionesDe(Aviso aviso) {
        Set<String> direcciones = new LinkedHashSet<>(aviso.correos());
        for (UUID id : aviso.destinatarios()) {
            // Una cuenta que ya no existe (se eliminó entre el aviso y el envío) se omite sin ruido.
            usuarios.buscarPorId(id).map(Usuario::correo).ifPresent(direcciones::add);
        }
        return direcciones;
    }

    private void enviarA(String direccion, Aviso aviso) {
        try {
            SimpleMailMessage correo = new SimpleMailMessage();
            correo.setFrom(remitente);
            correo.setTo(direccion);
            correo.setSubject(asuntoDe(aviso.tipo()));
            correo.setText(cuerpoDe(aviso));
            enviador.send(correo);
        } catch (RuntimeException e) {
            log.warn("no se pudo enviar el correo del aviso {}: {}", aviso.tipo(), e.getMessage());
        }
    }

    static String asuntoDe(TipoDeAviso tipo) {
        return switch (tipo) {
            case CAMBIO_DE_ESTADO -> "Cambió el estado de una de tus plantas";
            case DIAGNOSTICO_NUEVO -> "Nuevo diagnóstico de una planta";
            case ACCESO_PERDIDO -> "Perdiste el acceso a una planta";
            case INVITACION -> "Te invitaron a ver una planta en Fitodiagnóstico";
            case RESTABLECIMIENTO -> "Restablece tu contraseña de Fitodiagnóstico";
            case SOLICITUD_RESUELTA -> "Respondieron tu solicitud de especie";
        };
    }

    String cuerpoDe(Aviso aviso) {
        List<String> partes = new ArrayList<>();
        partes.add(aviso.mensaje());
        String token = aviso.datos().get(Aviso.TOKEN);
        if (token != null && aviso.tipo() == TipoDeAviso.INVITACION) {
            partes.add("Acepta la invitación aquí:\n" + enlaces.invitacion(token));
            partes.add("El enlace sirve una sola vez y caduca en " + Invitacion.VIGENCIA.toDays() + " días.");
        } else if (token != null && aviso.tipo() == TipoDeAviso.RESTABLECIMIENTO) {
            partes.add("Elige una contraseña nueva aquí:\n" + enlaces.restablecimiento(token));
            partes.add("El enlace sirve una sola vez y caduca en "
                    + SolicitudRestablecimiento.VIGENCIA.toMinutes() + " minutos.");
        }
        return String.join("\n\n", partes);
    }
}
