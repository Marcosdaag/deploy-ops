package com.marcosdaag.deployops.controller;

import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.service.EndpointManagerService;
import org.springframework.beans.factory.annotation.Autowired;
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
    public ServiceEntity addEndpoint(@RequestParam String name, @RequestParam String url) {
        return endpointManager.createEndpoint(name, url);
    }
}