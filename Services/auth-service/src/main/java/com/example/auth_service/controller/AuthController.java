package com.example.auth_service.controller;


import com.example.auth_service.dto.ForgotPasswordRequest;
import com.example.auth_service.dto.LoginRequest;
import com.example.auth_service.dto.RegisterRequest;
import com.example.auth_service.dto.UserInfo;
import com.example.auth_service.service.KeycloakService;
import jakarta.validation.Valid;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
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

    @GetMapping("/users/{userId}")
    public ResponseEntity<UserInfo> getUserById(
            @PathVariable String userId,
            @AuthenticationPrincipal Jwt jwt) {
        try {
            UserInfo user = keycloakService.getUserById(userId);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }


    @GetMapping("/users")
    public ResponseEntity<List<UserInfo>> getAllUsers() {
        try {
            List<UserInfo> users = keycloakService.searchUsers("");
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            log.error("Error fetching users", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }







    @GetMapping("/me")
    public ResponseEntity<UserInfo> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Jwt jwt = (Jwt) authentication.getPrincipal();

        // Extraire infos depuis JWT
        String userId = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        String firstName = jwt.getClaimAsString("given_name");
        String lastName = jwt.getClaimAsString("family_name");
        Boolean emailVerified = jwt.getClaimAsBoolean("email_verified");

        // Extraire rôles
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        List<String> roles = new ArrayList<>();
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            roles = (List<String>) realmAccess.get("roles");
        }

        UserInfo userInfo = UserInfo.builder()
                .userId(userId)
                .username(username)
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .emailVerified(emailVerified)
                .roles(roles)
                .build();

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



    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            keycloakService.createUser(request);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Utilisateur créé avec succès"));
        } catch (Exception e) {
            if (e.getMessage().contains("User exists")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "Ce nom d'utilisateur ou email est déjà utilisé"));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erreur lors de la création du compte"));
        }
    }




    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            keycloakService.sendPasswordResetEmail(request.getEmail());

            // Toujours retourner succès (sécurité - ne pas révéler si email existe)
            return ResponseEntity.ok(Map.of(
                    "message", "Si un compte existe avec cet email, vous recevrez un lien de réinitialisation"
            ));
        } catch (Exception e) {
            log.error("Error sending password reset email", e);
            // Toujours retourner succès
            return ResponseEntity.ok(Map.of(
                    "message", "Si un compte existe avec cet email, vous recevrez un lien de réinitialisation"
            ));
        }
    }
}