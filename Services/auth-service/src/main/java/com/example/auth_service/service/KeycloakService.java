package com.example.auth_service.service;

import com.example.auth_service.dto.RegisterRequest;
import com.example.auth_service.dto.UserInfo;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakService {

    // ✅ Bean injecté par Spring - NE PAS redéclarer localement dans les méthodes
    private final Keycloak keycloak;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.resource}")
    private String adminClientId;

    @Value("${keycloak.credentials.secret}")
    private String adminClientSecret;


    // ──────────────────────────────────────────────────────────────
    // LOGIN
    // ──────────────────────────────────────────────────────────────

    public Map<String, Object> login(String username, String password) {
        String tokenUrl = authServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", "visioconference-frontend");
        body.add("username", username);
        body.add("password", password);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
        return response.getBody();
    }


    // ──────────────────────────────────────────────────────────────
    // CRÉER UTILISATEUR
    // ──────────────────────────────────────────────────────────────

    public void createUser(RegisterRequest request) {
        // ✅ FIX : utilise getKeycloakInstance() au lieu de redéclarer localement
        Keycloak adminClient = getKeycloakInstance();

        try {
            RealmResource realmResource = adminClient.realm(realm);
            UsersResource usersResource = realmResource.users();

            UserRepresentation user = new UserRepresentation();
            user.setUsername(request.getUsername());
            user.setEmail(request.getEmail());
            user.setFirstName(request.getFirstName());
            user.setLastName(request.getLastName());
            user.setEnabled(true);
            user.setEmailVerified(true);

            Response response = usersResource.create(user);

            try {
                if (response.getStatus() == 409) {
                    throw new RuntimeException("User exists with same username or email");
                }
                if (response.getStatus() != 201) {
                    throw new RuntimeException("Failed to create user in Keycloak. Status: " + response.getStatus());
                }

                String userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");

                CredentialRepresentation credential = new CredentialRepresentation();
                credential.setType(CredentialRepresentation.PASSWORD);
                credential.setValue(request.getPassword());
                credential.setTemporary(false);

                UserResource userResource = usersResource.get(userId);
                userResource.resetPassword(credential);

                RoleRepresentation userRole = realmResource.roles().get("ROLE_USER").toRepresentation();
                userResource.roles().realmLevel().add(Collections.singletonList(userRole));

                log.info("✅ User created in Keycloak: {}", request.getUsername());

            } finally {
                // ✅ FIX : response.close() dans un finally pour éviter les fuites
                response.close();
            }

        } finally {
            // ✅ FIX : adminClient.close() dans un finally pour garantir la fermeture
            adminClient.close();
        }
    }


    // ──────────────────────────────────────────────────────────────
    // RESET MOT DE PASSE
    // ──────────────────────────────────────────────────────────────

    public void sendPasswordResetEmail(String email) {
        // ✅ FIX : utilise getKeycloakInstance() au lieu de redéclarer localement
        Keycloak adminClient = getKeycloakInstance();

        try {
            RealmResource realmResource = adminClient.realm(realm);
            UsersResource usersResource = realmResource.users();

            List<UserRepresentation> users = usersResource.search(null, null, null, email, 0, 1);

            if (users.isEmpty()) {
                log.warn("⚠️ No user found with email: {}", email);
                return;
            }

            String userId = users.get(0).getId();
            UserResource userResource = usersResource.get(userId);
            userResource.executeActionsEmail(Arrays.asList("UPDATE_PASSWORD"));

            log.info("✅ Password reset email sent to: {}", email);

        } finally {
            // ✅ FIX : toujours fermé même si une exception est levée
            adminClient.close();
        }
    }


    // ──────────────────────────────────────────────────────────────
    // RECHERCHER UTILISATEURS
    // ──────────────────────────────────────────────────────────────

    public List<UserInfo> searchUsers(String query) {
        Keycloak adminClient = getKeycloakInstance();

        try {
            RealmResource realmResource = adminClient.realm(realm);
            UsersResource usersResource = realmResource.users();

            return usersResource.search(query, 0, 10)
                    .stream()
                    .map(this::mapToUserInfo)
                    .collect(Collectors.toList());

        } finally {
            adminClient.close();
        }
    }


    // ──────────────────────────────────────────────────────────────
    // RÉCUPÉRER UTILISATEUR PAR ID
    // ──────────────────────────────────────────────────────────────

    public UserInfo getUserById(String userId) {
        Keycloak adminClient = getKeycloakInstance();

        try {
            UserRepresentation keycloakUser = adminClient.realm(realm)
                    .users()
                    .get(userId)
                    .toRepresentation();

            return mapToUserInfo(keycloakUser);

        } finally {
            adminClient.close();
        }
    }


    // ──────────────────────────────────────────────────────────────
    // HELPERS PRIVÉS
    // ──────────────────────────────────────────────────────────────

    private UserInfo mapToUserInfo(UserRepresentation keycloakUser) {
        List<String> roles = new ArrayList<>();
        if (keycloakUser.getRealmRoles() != null) {
            roles.addAll(keycloakUser.getRealmRoles());
        }

        return UserInfo.builder()
                .userId(keycloakUser.getId())
                .username(keycloakUser.getUsername())
                .email(keycloakUser.getEmail())
                .firstName(keycloakUser.getFirstName())
                .lastName(keycloakUser.getLastName())
                .emailVerified(keycloakUser.isEmailVerified())
                .roles(roles)
                .build();
    }

    /**
     * ✅ Source unique pour créer une instance Keycloak admin
     * Toutes les méthodes passent par ici — plus de duplication
     */
    private Keycloak getKeycloakInstance() {
        return KeycloakBuilder.builder()
                .serverUrl(authServerUrl)
                .realm(realm)
                .clientId(adminClientId)
                .clientSecret(adminClientSecret)
                .grantType("client_credentials")
                .build();
    }
}