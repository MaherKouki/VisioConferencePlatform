package com.example.auth_service.controller;

import com.example.auth_service.dto.ForgotPasswordRequest;
import com.example.auth_service.dto.LoginRequest;
import com.example.auth_service.dto.RegisterRequest;
import com.example.auth_service.dto.UserInfo;
import com.example.auth_service.service.KeycloakService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    // ═══════════════════════════════════════════════════════════
    // PUBLIC ENDPOINTS — pas de token requis
    // ═══════════════════════════════════════════════════════════

    /**
     * POST /api/auth/login
     * Public — pas de token requis
     */
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

    /**
     * POST /api/auth/register
     * Public — pas de token requis
     */
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

    /**
     * POST /api/auth/forgot-password
     * Public — pas de token requis
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            keycloakService.sendPasswordResetEmail(request.getEmail());
            return ResponseEntity.ok(Map.of(
                    "message", "Si un compte existe avec cet email, vous recevrez un lien de réinitialisation"
            ));
        } catch (Exception e) {
            log.error("Error sending password reset email", e);
            return ResponseEntity.ok(Map.of(
                    "message", "Si un compte existe avec cet email, vous recevrez un lien de réinitialisation"
            ));
        }
    }

    /**
     * GET /api/auth/public/health
     * Public — health check
     */
    @GetMapping("/public/health")
    public ResponseEntity<?> publicHealth() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "auth-service",
                "version", "1.0.0"
        ));
    }

    // ═══════════════════════════════════════════════════════════
    // AUTHENTICATED ENDPOINTS — ROLE_USER minimum
    // ═══════════════════════════════════════════════════════════

    /**
     * GET /api/auth/me
     * ✅ Sécurisé : ROLE_USER requis
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<UserInfo> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Jwt jwt = (Jwt) authentication.getPrincipal();

        String userId         = jwt.getSubject();
        String username       = jwt.getClaimAsString("preferred_username");
        String email          = jwt.getClaimAsString("email");
        String firstName      = jwt.getClaimAsString("given_name");
        String lastName       = jwt.getClaimAsString("family_name");
        Boolean emailVerified = jwt.getClaimAsBoolean("email_verified");

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

    /**
     * GET /api/auth/users/{userId}
     * ✅ Sécurisé : ROLE_USER requis
     *    Tout utilisateur connecté peut consulter un profil par ID
     */
    @GetMapping("/users/{userId}")
    @PreAuthorize("hasRole('ROLE_USER')")
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

    // ═══════════════════════════════════════════════════════════
    // ADMIN ENDPOINTS — ROLE_ADMIN requis
    // ═══════════════════════════════════════════════════════════

    /**
     * GET /api/auth/users
     *
     * ✅ FIX SÉCURITÉ : @PreAuthorize("hasRole('ROLE_ADMIN')") ajouté
     *
     * AVANT : aucune protection → n'importe quel user connecté pouvait
     *         récupérer tous les emails, noms, IDs Keycloak → violation RGPD
     *
     * APRÈS : seul un ROLE_ADMIN peut lister tous les utilisateurs
     */
    @GetMapping("/users")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<List<UserInfo>> getAllUsers() {
        try {
            List<UserInfo> users = keycloakService.getAllUsers();
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            log.error("Error fetching all users", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}