package com.vivero.fitodiagnostico.dobles;

import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * El "servidor SMTP" del perfil {@code test} (D54): en vez de abrir una conexión,
 * guarda los mensajes que el adaptador de correo habría enviado. Las pruebas
 * de contexto completo lo inyectan y afirman sobre ellos. Vive en src/test y se
 * activa solo con el perfil {@code test}; ocupa el lugar del {@code JavaMailSender}
 * que Spring Boot crearía en un entorno real.
 */
@Component
@Profile("test")
public class CorreoCapturado implements MailSender {

    private final List<SimpleMailMessage> enviados = new CopyOnWriteArrayList<>();

    @Override
    public void send(SimpleMailMessage mensaje) throws MailException {
        enviados.add(mensaje);
    }

    @Override
    public void send(SimpleMailMessage... mensajes) throws MailException {
        enviados.addAll(List.of(mensajes));
    }

    public List<SimpleMailMessage> enviados() {
        return List.copyOf(enviados);
    }

    /** Los enviados a esa dirección. */
    public List<SimpleMailMessage> para(String direccion) {
        return enviados.stream()
                .filter(m -> m.getTo() != null && List.of(m.getTo()).contains(direccion)).toList();
    }

    public void limpiar() {
        enviados.clear();
    }
}
