package com.example.auth_service.controller;


import com.example.auth_service.dto.UserInfo;
import com.example.auth_service.service.KeycloakService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════
 * USER PROFILE CONTROLLER
 * ═══════════════════════════════════════════════════════════
 *
 * Endpoints pour gestion profil utilisateur
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final KeycloakService keycloakService;

    /**
     * ═══════════════════════════════════════════════════════════
     * RECHERCHER UTILISATEURS (pour ajouter à groupe)
     * ═══════════════════════════════════════════════════════════
     *
     * GET /api/users/search?query=alice
     */
    @GetMapping("/search")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<List<UserInfo>> searchUsers(@RequestParam String query) {
        List<UserInfo> users = keycloakService.searchUsers(query);
        return ResponseEntity.ok(users);
    }

    /**
     * ═══════════════════════════════════════════════════════════
     * RÉCUPÉRER INFOS UTILISATEUR PAR ID
     * ═══════════════════════════════════════════════════════════
     *
     * GET /api/users/{userId}
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<UserInfo> getUserById(@PathVariable String userId) {
        UserInfo user = keycloakService.getUserById(userId);
        return ResponseEntity.ok(user);
    }
}