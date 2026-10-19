package com.marcosdaag.deployops.model.enums;

/**
 * Representa los estados del ciclo de vida de un servicio monitoreado
 * según la máquina de estados definida en docs/DOMAIN_AND_RULES.md.
 */
public enum ServiceStatus {
    HEALTHY,    // El servicio responde con 2xx de manera estable
    DEGRADED,   // Alerta temprana: ha fallado 1 o 2 veces consecutivas
    DOWN,       // Caída confirmada: 3 fallos consecutivos (abre incidente)
    RECOVERING  // Recuperación en progreso: 1er éxito tras una caída
}
