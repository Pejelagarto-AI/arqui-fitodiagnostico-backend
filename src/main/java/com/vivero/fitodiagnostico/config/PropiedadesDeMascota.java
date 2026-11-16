package com.vivero.fitodiagnostico.config;

import com.vivero.fitodiagnostico.mascota.modelo.ParametrosDeMascota;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code fitodiagnostico.mascota.*} (D139). Cada campo arranca con la constante de {@link ParametrosDeMascota}: sin
 * configurar nada, la mascota usa los valores documentados. Las duraciones van en ISO-8601 o en el formato corto de
 * Spring ({@code 15m}, {@code 2h}).
 */
@ConfigurationProperties(prefix = "fitodiagnostico.mascota")
public class PropiedadesDeMascota {

    private double vitalidadInicial = ParametrosDeMascota.VITALIDAD_INICIAL;
    private double vitalidadAlRevivir = ParametrosDeMascota.VITALIDAD_AL_REVIVIR;
    private double perdidaPorHoraEnCritico = ParametrosDeMascota.PERDIDA_POR_HORA_EN_CRITICO;
    private double perdidaPorHoraEnRiesgo = ParametrosDeMascota.PERDIDA_POR_HORA_EN_RIESGO;
    private double gananciaPorHoraSaludable = ParametrosDeMascota.GANANCIA_POR_HORA_SALUDABLE;
    private Duration tiempoParaRevivir = ParametrosDeMascota.TIEMPO_PARA_REVIVIR;
    private Duration ventanaSinLecturas = ParametrosDeMascota.VENTANA_SIN_LECTURAS;
    private Duration duracionDelDiaDeRacha = ParametrosDeMascota.DURACION_DEL_DIA_DE_RACHA;
    private double subidaMinimaDeRiego = ParametrosDeMascota.SUBIDA_MINIMA_DE_RIEGO;
    private double factorDeSol = ParametrosDeMascota.FACTOR_DE_SOL;
    private double subidaMinimaDeSol = ParametrosDeMascota.SUBIDA_MINIMA_DE_SOL;
    private Duration ventanaDeReferencia = ParametrosDeMascota.VENTANA_DE_REFERENCIA;
    private int muestrasMinimasDeReferencia = ParametrosDeMascota.MUESTRAS_MINIMAS_DE_REFERENCIA;
    private int lecturasARevisar = ParametrosDeMascota.LECTURAS_A_REVISAR;
    private Duration enfriamientoDeEventos = ParametrosDeMascota.ENFRIAMIENTO_DE_EVENTOS;
    private Duration plazoParaConfirmarRiego = ParametrosDeMascota.PLAZO_PARA_CONFIRMAR_RIEGO;
    private double vitalidadCansada = ParametrosDeMascota.VITALIDAD_CANSADA;

    ParametrosDeMascota aParametros() {
        return new ParametrosDeMascota(vitalidadInicial, vitalidadAlRevivir, perdidaPorHoraEnCritico,
                perdidaPorHoraEnRiesgo, gananciaPorHoraSaludable, tiempoParaRevivir, ventanaSinLecturas,
                duracionDelDiaDeRacha, subidaMinimaDeRiego, factorDeSol, subidaMinimaDeSol, ventanaDeReferencia,
                muestrasMinimasDeReferencia, lecturasARevisar, enfriamientoDeEventos, plazoParaConfirmarRiego,
                vitalidadCansada);
    }

    public double getVitalidadInicial() { return vitalidadInicial; }
    public void setVitalidadInicial(double v) { this.vitalidadInicial = v; }
    public double getVitalidadAlRevivir() { return vitalidadAlRevivir; }
    public void setVitalidadAlRevivir(double v) { this.vitalidadAlRevivir = v; }
    public double getPerdidaPorHoraEnCritico() { return perdidaPorHoraEnCritico; }
    public void setPerdidaPorHoraEnCritico(double v) { this.perdidaPorHoraEnCritico = v; }
    public double getPerdidaPorHoraEnRiesgo() { return perdidaPorHoraEnRiesgo; }
    public void setPerdidaPorHoraEnRiesgo(double v) { this.perdidaPorHoraEnRiesgo = v; }
    public double getGananciaPorHoraSaludable() { return gananciaPorHoraSaludable; }
    public void setGananciaPorHoraSaludable(double v) { this.gananciaPorHoraSaludable = v; }
    public Duration getTiempoParaRevivir() { return tiempoParaRevivir; }
    public void setTiempoParaRevivir(Duration v) { this.tiempoParaRevivir = v; }
    public Duration getVentanaSinLecturas() { return ventanaSinLecturas; }
    public void setVentanaSinLecturas(Duration v) { this.ventanaSinLecturas = v; }
    public Duration getDuracionDelDiaDeRacha() { return duracionDelDiaDeRacha; }
    public void setDuracionDelDiaDeRacha(Duration v) { this.duracionDelDiaDeRacha = v; }
    public double getSubidaMinimaDeRiego() { return subidaMinimaDeRiego; }
    public void setSubidaMinimaDeRiego(double v) { this.subidaMinimaDeRiego = v; }
    public double getFactorDeSol() { return factorDeSol; }
    public void setFactorDeSol(double v) { this.factorDeSol = v; }
    public double getSubidaMinimaDeSol() { return subidaMinimaDeSol; }
    public void setSubidaMinimaDeSol(double v) { this.subidaMinimaDeSol = v; }
    public Duration getVentanaDeReferencia() { return ventanaDeReferencia; }
    public void setVentanaDeReferencia(Duration v) { this.ventanaDeReferencia = v; }
    public int getMuestrasMinimasDeReferencia() { return muestrasMinimasDeReferencia; }
    public void setMuestrasMinimasDeReferencia(int v) { this.muestrasMinimasDeReferencia = v; }
    public int getLecturasARevisar() { return lecturasARevisar; }
    public void setLecturasARevisar(int v) { this.lecturasARevisar = v; }
    public Duration getEnfriamientoDeEventos() { return enfriamientoDeEventos; }
    public void setEnfriamientoDeEventos(Duration v) { this.enfriamientoDeEventos = v; }
    public Duration getPlazoParaConfirmarRiego() { return plazoParaConfirmarRiego; }
    public void setPlazoParaConfirmarRiego(Duration v) { this.plazoParaConfirmarRiego = v; }
    public double getVitalidadCansada() { return vitalidadCansada; }
    public void setVitalidadCansada(double v) { this.vitalidadCansada = v; }
}
