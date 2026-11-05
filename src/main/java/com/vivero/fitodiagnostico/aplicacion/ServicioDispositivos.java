package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.excepcion.CredencialRechazadaException;
import com.vivero.fitodiagnostico.dominio.excepcion.DispositivoNoEncontradoException;
import com.vivero.fitodiagnostico.dominio.excepcion.DispositivoSinReclamarException;
import com.vivero.fitodiagnostico.dominio.excepcion.DispositivoYaExisteException;
import com.vivero.fitodiagnostico.dominio.excepcion.FirmaDeFlotaInvalidaException;
import com.vivero.fitodiagnostico.dominio.modelo.Dispositivo;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.Reto;
import com.vivero.fitodiagnostico.dominio.modelo.TokenDeDispositivo;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.AutenticarDispositivoPort;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.AutorregistrarDispositivoPort;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.DescubrirDispositivosPort;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.ReclamarDispositivoPort;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.VerificarVinculoPort;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.RegistrarDispositivoDeFabricaPort;
import com.vivero.fitodiagnostico.dominio.puerto.salida.AutenticadorDeDispositivos;
import com.vivero.fitodiagnostico.dominio.puerto.salida.EmisorTokenDispositivoPort;
import com.vivero.fitodiagnostico.dominio.puerto.salida.GeneradorDeTokens;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioAccesos;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioDispositivos;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioPlantas;
import com.vivero.fitodiagnostico.dominio.servicio.MismaRed;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAcceso;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Los casos de uso del ciclo de vida de un dispositivo (fase 3), uno por actor:
 * la FÁBRICA da de alta (serial + secreto), el DUEÑO reclama por serial y el DISPOSITIVO se autentica con
 * un reto. Fase 3b: el DISPOSITIVO también puede darse de alta solo, firmando con la clave de flota. Reemplaza al flujo de la fase 2, donde el dueño recibía un token y lo copiaba al chip.
 *
 * <p>La regla "solo el dueño" es de {@link PoliticaDeAcceso} (a través de {@link AutorizadorDePlantas},
 * igual que editar la planta). Qué tiene que ser cierto para entregar una credencial lo deciden el
 * dominio ({@link Dispositivo}: reclamo, reto de un solo uso) y el puerto {@link AutenticadorDeDispositivos}
 * (la comparación de la respuesta); firmar el token es del puerto {@link EmisorTokenDispositivoPort}. Este
 * servicio solo los orquesta y no sabe qué es un HMAC ni un JWT.
 *
 * <p>El rol ADMINISTRADOR de la fábrica lo exige la cadena de seguridad en {@code /api/v1/admin/**}, como
 * en los demás casos de uso de administración; aquí el administrador llega como un id y no se vuelve a comprobar.
 */
@Service
public class ServicioDispositivos implements RegistrarDispositivoDeFabricaPort, ReclamarDispositivoPort,
        AutenticarDispositivoPort, AutorregistrarDispositivoPort, DescubrirDispositivosPort, VerificarVinculoPort {

    /** Lo que ve el usuario si escanea el sticker antes de que la matera haya prendido por primera vez. */
    static final String MENSAJE_RECLAMO_SIN_REGISTRO = "no existe o la matera no ha encendido todavía";

    /** Sin 0/O/1/I para que nadie confunda letras con números al teclear el serial del sticker. */
    private static final String ALFABETO_DEL_SERIAL = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String ALFABETO_BASE64_URL =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    private static final int LARGO_DEL_SERIAL = 6;

    private final AutorizadorDePlantas autorizador;
    private final RepositorioDispositivos dispositivos;
    private final EmisorTokenDispositivoPort emisor;
    private final AutenticadorDeDispositivos autenticador;
    private final GeneradorDeTokens generador;
    private final Clock reloj;

    public ServicioDispositivos(RepositorioPlantas plantas, RepositorioAccesos accesos, PoliticaDeAcceso politica,
                                RepositorioDispositivos dispositivos, EmisorTokenDispositivoPort emisor,
                                AutenticadorDeDispositivos autenticador, GeneradorDeTokens generador, Clock reloj) {
        this.autorizador = new AutorizadorDePlantas(plantas, accesos, politica);
        this.dispositivos = dispositivos;
        this.emisor = emisor;
        this.autenticador = autenticador;
        this.generador = generador;
        this.reloj = reloj;
    }

    // ---- fábrica (administrador) ----

    @Override
    @Transactional
    public DispositivoDeFabrica registrar(UUID administrador, Optional<String> serialElegido) {
        String serial = serialElegido.filter(s -> !s.isBlank()).orElseGet(this::serialNuevo);
        if (dispositivos.porSerial(serial).isPresent()) {
            throw new DispositivoYaExisteException(serial);
        }
        Dispositivo nuevo = Dispositivo.deFabrica(serial, generador.generar(), ahora());
        dispositivos.guardar(nuevo);
        return new DispositivoDeFabrica(nuevo.serial(), nuevo.secreto(), nuevo.creadoEn());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Dispositivo> listar(UUID administrador) {
        return dispositivos.todos();
    }

    // ---- auto-registro (el propio dispositivo) ----

    /**
     * Regla: solo se autorregistra lo que firma la flota. La firma se verifica ANTES de mirar si el serial
     * existe, así quien no conoce la clave no puede averiguar qué seriales hay (siempre 401). Un serial que ya
     * existe con el mismo secreto es el chip reiniciado: se responde lo que hay sin tocar nada (idempotente).
     * Con otro secreto es un intento de pisar una matera real: 409.
     */
    @Override
    @Transactional
    public Resultado registrar(String serial, String secreto, String firma, String ip) {
        if (!autenticador.firmaDeFlotaValida(serial, secreto, firma)) {
            throw new FirmaDeFlotaInvalidaException();
        }
        Optional<Dispositivo> existente = dispositivos.porSerial(serial);
        if (existente.isPresent()) {
            if (!existente.get().tieneElSecreto(secreto)) {
                throw new DispositivoYaExisteException(serial);
            }
            existente.get().registrarContacto(ahora(), ip);
            dispositivos.guardar(existente.get());
            return new Resultado(existente.get(), false);
        }
        Dispositivo nuevo = Dispositivo.autorregistrado(serial, secreto, ahora());
        nuevo.registrarContacto(nuevo.creadoEn(), ip);
        dispositivos.guardar(nuevo);
        return new Resultado(nuevo, true);
    }

    // ---- dueño ----

    @Override
    @Transactional
    public Dispositivo reclamar(UUID solicitante, UUID plantaId, String serial) {
        Planta planta = autorizador.paraModificar(solicitante, plantaId);
        Dispositivo dispositivo = dispositivos.porSerial(serial)
                .orElseThrow(() -> new DispositivoNoEncontradoException(MENSAJE_RECLAMO_SIN_REGISTRO, serial));
        dispositivo.reclamar(planta.id(), ahora());
        dispositivos.guardar(dispositivo);
        return dispositivo;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Dispositivo> listar(UUID solicitante, UUID plantaId) {
        Planta planta = autorizador.paraModificar(solicitante, plantaId);
        return dispositivos.porPlanta(planta.id());
    }

    @Override
    @Transactional
    public void desvincular(UUID solicitante, UUID plantaId, String serial) {
        Planta planta = autorizador.paraModificar(solicitante, plantaId);
        Dispositivo dispositivo = dispositivos.porSerial(serial)
                .filter(d -> d.plantaId().filter(planta.id()::equals).isPresent())
                .orElseThrow(() -> new DispositivoNoEncontradoException(serial));
        dispositivo.desvincular();
        dispositivos.guardar(dispositivo);
    }

    // ---- vínculo vigente (cada lectura) ----

    @Override
    @Transactional(readOnly = true)
    public void exigirVinculo(String serial, UUID plantaId) {
        boolean vinculado = dispositivos.porSerial(serial)
                .flatMap(Dispositivo::plantaId)
                .filter(plantaId::equals)
                .isPresent();
        if (!vinculado) {
            throw new CredencialRechazadaException(serial);
        }
    }

    // ---- descubrimiento ----

    /**
     * Las materas sin dueño que acaban de hablar con el servidor desde la misma red que quien pregunta. El
     * {@code usuario} solo identifica que hay sesión de persona (la ruta ya lo exige); no filtra nada: una matera
     * sin dueño no es de nadie.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Cercano> cercanos(UUID usuario, String ipDelSolicitante) {
        Instant ahora = ahora();
        return dispositivos.todos().stream()
                .filter(d -> d.estaCercaSinReclamar(ahora, VENTANA_DE_CERCANIA))
                .filter(d -> MismaRed.entre(ipDelSolicitante, d.ultimaIp().orElse(null)))
                .map(d -> new Cercano(d, Math.max(0, Duration.between(d.ultimoContactoEn().orElseThrow(), ahora).toSeconds())))
                .sorted(Comparator.comparingLong(Cercano::vistoHaceSegundos))
                .toList();
    }

    // ---- dispositivo ----

    @Override
    @Transactional
    public Reto reto(String serial, String ip) {
        Dispositivo dispositivo = dispositivos.porSerial(serial)
                .orElseThrow(() -> new DispositivoNoEncontradoException(serial));
        Instant ahora = ahora();
        dispositivo.registrarContacto(ahora, ip);
        Reto reto = dispositivo.asignarReto(retoNuevo(), ahora.plus(Dispositivo.VIGENCIA_DEL_RETO));
        dispositivos.guardar(dispositivo);
        return reto;
    }

    /**
     * El reto se gasta ANTES de decidir y ese gasto se conserva aunque la decisión sea un rechazo: por eso
     * las dos excepciones de negocio no deshacen la transacción. Si se deshiciera, quien adivina respuestas
     * podría reintentar contra el mismo reto indefinidamente (ver D74).
     */
    @Override
    @Transactional(noRollbackFor = {DispositivoSinReclamarException.class, CredencialRechazadaException.class})
    public TokenDeDispositivo credencial(String serial, String respuesta, String ip) {
        Dispositivo dispositivo = dispositivos.porSerial(serial)
                .orElseThrow(() -> new DispositivoNoEncontradoException(serial));
        Instant ahora = ahora();
        dispositivo.registrarContacto(ahora, ip);
        Optional<String> reto = dispositivo.consumirReto(ahora);
        dispositivos.guardar(dispositivo);

        UUID plantaId = dispositivo.plantaId().orElseThrow(() -> new DispositivoSinReclamarException(serial));
        boolean valida = reto.isPresent() && autenticador.respuestaValida(dispositivo.secreto(), reto.get(), respuesta);
        if (!valida) {
            throw new CredencialRechazadaException(serial);
        }

        dispositivo.registrarCredencial(ahora);
        dispositivos.guardar(dispositivo);
        return emisor.emitir(dispositivo.serial(), plantaId);
    }

    /**
     * El instante a microsegundos, que es la precisión de la columna: lo que el servicio devuelve en la
     * respuesta es idéntico a lo que la base guardó y a lo que mostrará la siguiente lectura.
     */
    private Instant ahora() {
        return reloj.instant().truncatedTo(ChronoUnit.MICROS);
    }

    // ---- valores aleatorios (reusan el generador de tokens: una sola fuente de azar) ----

    /** {@code fito-} y 6 caracteres del alfabeto sin ambiguos; 64 símbolos de base64url caben exacto en 32 (mod). */
    private String serialNuevo() {
        String azar = generador.generar();
        StringBuilder serial = new StringBuilder("fito-");
        for (int i = 0; i < LARGO_DEL_SERIAL; i++) {
            int indice = ALFABETO_BASE64_URL.indexOf(azar.charAt(i)) % ALFABETO_DEL_SERIAL.length();
            serial.append(ALFABETO_DEL_SERIAL.charAt(indice));
        }
        return serial.toString();
    }

    /**
     * 32 bytes aleatorios en base64 ESTÁNDAR con relleno (44 caracteres), que es lo que fija el contrato: el
     * generador entrega base64url sin relleno, que se traduce símbolo a símbolo. El reto es opaco para el chip.
     */
    private String retoNuevo() {
        return generador.generar().replace('-', '+').replace('_', '/') + "=";
    }
}
