package com.marcosdaag.deployops.controller;

import com.marcosdaag.deployops.dto.EndpointCreateDTO;
import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.service.EndpointManagerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/endpoints")
public class EndpointController {

    @Autowired
    private EndpointManagerService endpointManager; // Inyectamos al servicio

    // GET: http://localhost:8080/api/endpoints
    @GetMapping
    public List<ServiceEntity> getEndpoints() {
        return endpointManager.getAllEndpoints();
    }

    // POST: http://localhost:8080/api/endpoints?name=Google&url=https://google.com
    @PostMapping
    public ResponseEntity<ServiceEntity> createEndpoint(@Valid @RequestBody EndpointCreateDTO dto) {
        ServiceEntity created = endpointManager.createEndpoint(dto);
        return ResponseEntity.ok(created);
    }
    // DELETE: Borrar por ID
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEndpoint(@PathVariable Long id) {
        endpointManager.deleteEndpoint(id);
        return ResponseEntity.noContent().build(); // Devuelve un 204 No Content (Éxito sin cuerpo)
    }
}