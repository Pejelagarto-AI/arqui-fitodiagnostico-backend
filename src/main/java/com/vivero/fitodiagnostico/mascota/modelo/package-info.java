/**
 * Contexto {@code mascota} (fase 9): la planta como tamagotchi.
 *
 * <p>Este paquete es el modelo del contexto: reglas puras (vitalidad, racha, detección de riego y de sol, ánimo,
 * confirmación de "la regué") sin Spring ni puertos. Lee tipos del modelo de diagnóstico ({@code EstadoGlobal},
 * {@code Diagnostico}, {@code ResultadoParametro}) pero el diagnóstico no sabe que existe: la flecha va de la
 * mascota al dominio, nunca al revés (D130, ArchUnit {@code el_nucleo_no_conoce_a_la_mascota}).
 */
package com.vivero.fitodiagnostico.mascota.modelo;
