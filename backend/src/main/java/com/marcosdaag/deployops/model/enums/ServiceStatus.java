package com.marcosdaag.deployops.model.enums;

public enum ServiceStatus {
    PENDING,    // Recien creado, todavia no se le hizo ping
    HEALTHY,    // Funciona bien
    DEGRADED,   // Fallo 1 o 2 veces seguidas (alerta)
    DOWN,       // Caido (3 fallos seguidos, abre incidente)
    RECOVERING  // Volvio a responder tras estar caido (1er exito)
}
