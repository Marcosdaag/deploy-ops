package com.marcosdaag.deployops.dto;

import com.marcosdaag.deployops.model.UserEntity;
import lombok.Getter;

@Getter
public class AuthResponse {
    private final String token;
    private final Long id;
    private final String name;
    private final String email;
    private final String role;
    private final String provider;
    private final String publicSlug;

    public AuthResponse(String token, UserEntity user) {
        this.token = token;
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.role = user.getRole().name();
        this.provider = user.getProvider().name();
        this.publicSlug = user.getPublicSlug();
    }
}
