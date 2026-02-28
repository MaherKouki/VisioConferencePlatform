package com.example.auth_service.controller;


import com.example.auth_service.dto.LoginRequest;
import com.example.auth_service.dto.UserInfo;
import com.example.auth_service.service.KeycloakService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final KeycloakService keycloakService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            Map<String, Object> tokens = keycloakService.login(
                    request.getUsername(),
                    request.getPassword()
            );
            return ResponseEntity.ok(tokens);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid credentials"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<UserInfo> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        UserInfo userInfo = keycloakService.getUserInfo(jwt);
        return ResponseEntity.ok(userInfo);
    }

    @GetMapping("/public/health")
    public ResponseEntity<?> publicHealth() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "auth-service",
                "version", "1.0.0"
        ));
    }
}