package com.vivero.fitodiagnostico.dominio.modelo;

import com.vivero.fitodiagnostico.dominio.excepcion.DispositivoYaReclamadoException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las reglas del dispositivo de fábrica, sin Spring, sin base y sin reloj: el instante es un parámetro. */
class DispositivoTest {

    private static final Instant T0 = Instant.parse("2026-11-03T10:00:00Z");

    private final UUID planta = UUID.randomUUID();

    private Dispositivo deFabrica() {
        return Dispositivo.deFabrica("fito-7K2M9Q", "secreto-opaco", T0);
    }

    // ---- nacimiento ----

    @Test
    void sale_de_fabrica_sin_planta_sin_reto_y_sin_credencial() {
        Dispositivo d = deFabrica();

        assertThat(d.serial()).isEqualTo("fito-7K2M9Q");
        assertThat(d.estaReclamado()).isFalse();
        assertThat(d.plantaId()).isEmpty();
        assertThat(d.reclamadoEn()).isEmpty();
        assertThat(d.ultimaCredencialEn()).isEmpty();
        assertThat(d.reto()).isEmpty();
        assertThat(d.creadoEn()).isEqualTo(T0);
    }

    @Test
    void exige_serial_con_formato_secreto_y_fecha() {
        assertThatThrownBy(() -> Dispositivo.deFabrica(null, "s", T0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Dispositivo.deFabrica("con espacios", "s", T0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Dispositivo.deFabrica("a".repeat(65), "s", T0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Dispositivo.deFabrica("fito-1", " ", T0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Dispositivo.deFabrica("fito-1", "s", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void planta_y_fecha_de_reclamo_van_juntas() {
        assertThatThrownBy(() -> new Dispositivo("fito-1", "s", planta, null, null, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Dispositivo("fito-1", "s", null, T0, null, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void el_toString_no_muestra_el_secreto() {
        assertThat(deFabrica().toString()).doesNotContain("secreto-opaco");
    }

    // ---- auto-registro (fase 3b) ----

    private static final String SECRETO_LARGO = "s".repeat(32);

    @Test
    void autorregistrado_nace_sin_planta_y_marcado_como_de_la_flota() {
        Dispositivo d = Dispositivo.autorregistrado("fito-7K2M9Q", SECRETO_LARGO, T0);

        assertThat(d.registradoPor()).isEqualTo(OrigenDeRegistro.FLOTA);
        assertThat(d.estaReclamado()).isFalse();
        assertThat(d.reto()).isEmpty();
        assertThat(d.creadoEn()).isEqualTo(T0);
        assertThat(d.tieneElSecreto(SECRETO_LARGO)).isTrue();
    }

    @Test
    void de_fabrica_y_reconstruido_sin_origen_son_del_administrador() {
        assertThat(deFabrica().registradoPor()).isEqualTo(OrigenDeRegistro.ADMIN);
        assertThat(new Dispositivo("fito-1", "s", null, null, null, null, T0).registradoPor())
                .isEqualTo(OrigenDeRegistro.ADMIN);
    }

    @Test
    void autorregistrado_exige_secreto_de_32_caracteres_y_serial_con_formato() {
        assertThatThrownBy(() -> Dispositivo.autorregistrado("fito-1", "s".repeat(31), T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Dispositivo.autorregistrado("fito-1", null, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Dispositivo.autorregistrado("con espacios", SECRETO_LARGO, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tieneElSecreto_distingue_secretos_y_tolera_nulo() {
        Dispositivo d = Dispositivo.autorregistrado("fito-1", SECRETO_LARGO, T0);

        assertThat(d.tieneElSecreto(SECRETO_LARGO + "x")).isFalse();
        assertThat(d.tieneElSecreto(null)).isFalse();
    }

    @Test
    void el_origen_es_obligatorio_al_reconstruir() {
        assertThatThrownBy(() -> new Dispositivo("fito-1", "s", null, null, null, null, T0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- reclamo ----

    @Test
    void reclamar_lo_ata_a_la_planta_y_guarda_cuando() {
        Dispositivo d = deFabrica();

        d.reclamar(planta, T0.plusSeconds(5));

        assertThat(d.estaReclamado()).isTrue();
        assertThat(d.plantaId()).contains(planta);
        assertThat(d.reclamadoEn()).contains(T0.plusSeconds(5));
    }

    @Test
    void reclamar_de_nuevo_para_la_misma_planta_es_idempotente_y_conserva_la_fecha_original() {
        Dispositivo d = deFabrica();
        d.reclamar(planta, T0.plusSeconds(5));

        d.reclamar(planta, T0.plusSeconds(99));

        assertThat(d.reclamadoEn()).contains(T0.plusSeconds(5));
    }

    @Test
    void reclamar_uno_de_otra_planta_falla_y_no_cambia_nada() {
        Dispositivo d = deFabrica();
        d.reclamar(planta, T0);

        assertThatThrownBy(() -> d.reclamar(UUID.randomUUID(), T0.plusSeconds(1)))
                .isInstanceOf(DispositivoYaReclamadoException.class)
                .hasMessageContaining("fito-7K2M9Q");
        assertThat(d.plantaId()).contains(planta);
    }

    @Test
    void desvincular_lo_deja_sin_reclamar_sin_credencial_y_sin_reto() {
        Dispositivo d = deFabrica();
        d.reclamar(planta, T0);
        d.registrarCredencial(T0.plusSeconds(3));
        d.asignarReto("reto", T0.plusSeconds(60));

        d.desvincular();

        assertThat(d.estaReclamado()).isFalse();
        assertThat(d.plantaId()).isEmpty();
        assertThat(d.reclamadoEn()).isEmpty();
        assertThat(d.ultimaCredencialEn()).isEmpty();
        assertThat(d.reto()).isEmpty();
        // y puede reclamarlo otra planta
        d.reclamar(UUID.randomUUID(), T0.plusSeconds(10));
        assertThat(d.estaReclamado()).isTrue();
    }

    // ---- reto de un solo uso ----

    @Test
    void consumir_un_reto_vigente_lo_devuelve_y_lo_borra() {
        Dispositivo d = deFabrica();
        d.asignarReto("reto-1", T0.plus(Dispositivo.VIGENCIA_DEL_RETO));

        assertThat(d.consumirReto(T0.plusSeconds(10))).contains("reto-1");
        assertThat(d.reto()).isEmpty();
        assertThat(d.consumirReto(T0.plusSeconds(11))).isEmpty();   // un solo uso
    }

    @Test
    void consumir_sin_reto_no_devuelve_nada() {
        assertThat(deFabrica().consumirReto(T0)).isEmpty();
    }

    @Test
    void un_reto_vencido_no_se_devuelve_pero_igual_se_borra() {
        Dispositivo d = deFabrica();
        d.asignarReto("reto-1", T0.plusSeconds(60));

        assertThat(d.consumirReto(T0.plusSeconds(60))).isEmpty();   // en el instante exacto ya venció
        assertThat(d.reto()).isEmpty();
    }

    @Test
    void pedir_otro_reto_reemplaza_al_anterior() {
        Dispositivo d = deFabrica();
        d.asignarReto("viejo", T0.plusSeconds(60));
        d.asignarReto("nuevo", T0.plusSeconds(61));

        assertThat(d.consumirReto(T0.plusSeconds(1))).contains("nuevo");
    }

    @Test
    void el_reto_exige_valor_y_caducidad() {
        assertThatThrownBy(() -> deFabrica().asignarReto(" ", T0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> deFabrica().asignarReto("r", null)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- credencial ----

    @Test
    void registrar_credencial_guarda_la_ultima_vez() {
        Dispositivo d = deFabrica();
        d.reclamar(planta, T0);

        d.registrarCredencial(T0.plusSeconds(7));
        assertThat(d.ultimaCredencialEn()).contains(T0.plusSeconds(7));

        d.registrarCredencial(T0.plusSeconds(3600));
        assertThat(d.ultimaCredencialEn()).contains(T0.plusSeconds(3600));
    }
}
