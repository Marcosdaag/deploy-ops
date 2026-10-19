package com.marcosdaag.deployops.controller;

import com.marcosdaag.deployops.dto.EndpointCreateDTO;
import com.marcosdaag.deployops.dto.EndpointResponseDTO;
import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.service.EndpointManagerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/endpoints")
@RequiredArgsConstructor
public class EndpointController {

    private final EndpointManagerService endpointManager;

    // GET: http://localhost:8080/api/endpoints
    @GetMapping
    public ResponseEntity<List<EndpointResponseDTO>> getEndpoints() {
        return ResponseEntity.ok(endpointManager.getDashboardData());
    }

    // POST: http://localhost:8080/api/endpoints
    @PostMapping
    public ResponseEntity<EndpointResponseDTO> createEndpoint(@Valid @RequestBody EndpointCreateDTO dto) {
        ServiceEntity created = endpointManager.createEndpoint(dto);
        // Al crearse, su disponibilidad inicial es del 100%
        EndpointResponseDTO response = new EndpointResponseDTO(created, 100.0);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // DELETE: http://localhost:8080/api/endpoints/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEndpoint(@PathVariable Long id) {
        endpointManager.deleteEndpoint(id);
        return ResponseEntity.noContent().build();
    }
}