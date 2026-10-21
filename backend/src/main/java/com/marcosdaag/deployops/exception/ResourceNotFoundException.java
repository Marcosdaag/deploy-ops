package com.marcosdaag.deployops.exception;

// Excepcion para devolver un error 404 cuando buscan o borran un ID que no existe
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
