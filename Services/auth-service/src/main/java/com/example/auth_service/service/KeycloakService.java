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

    // ══════════════════════════════════════════════════════════════
    // LOGIN
    // ══════════════════════════════════════════════════════════════
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

    // ══════════════════════════════════════════════════════════════
    // REGISTER
    // ══════════════════════════════════════════════════════════════
    public void createUser(RegisterRequest request) {
        Keycloak adminClient = getKeycloakInstance();

        try {
            RealmResource realmResource = adminClient.realm(realm);
            UsersResource usersResource = realmResource.users();

            // ── 1. Créer l'utilisateur ────────────────────────────
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

                // ── 2. Récupérer l'ID du nouvel utilisateur ───────
                String userId = response.getLocation().getPath()
                        .replaceAll(".*/([^/]+)$", "$1");

                log.info("✅ User created in Keycloak with ID: {}", userId);

                // ── 3. Définir le mot de passe ────────────────────
                CredentialRepresentation credential = new CredentialRepresentation();
                credential.setType(CredentialRepresentation.PASSWORD);
                credential.setValue(request.getPassword());
                credential.setTemporary(false);

                UserResource userResource = usersResource.get(userId);
                userResource.resetPassword(credential);

                log.info("✅ Password set for user: {}", request.getUsername());

                // ── 4. Assigner ROLE_USER ─────────────────────────
                //
                // FIX : on vérifie que ROLE_USER existe dans Keycloak
                // avant de l'assigner. Si absent → exception claire.
                //
                // POURQUOI ce fix est nécessaire :
                //   Sans vérification, realmResource.roles().get("ROLE_USER")
                //   lance une NotFoundException si le rôle n'existe pas.
                //   Cette exception était catchée silencieusement dans
                //   AuthController → user créé SANS aucun rôle métier
                //   → 403 sur tous les endpoints @PreAuthorize à la connexion
                //
                assignRoleToUser(realmResource, userResource, "ROLE_USER", request.getUsername());

            } finally {
                response.close();
            }

        } finally {
            adminClient.close();
        }
    }

    /**
     * Assigne un rôle realm à un utilisateur Keycloak.
     * Vérifie d'abord que le rôle existe, lance une exception claire sinon.
     *
     * @param realmResource  le realm Keycloak
     * @param userResource   l'utilisateur cible
     * @param roleName       le nom du rôle (ex: "ROLE_USER")
     * @param username       pour les logs uniquement
     */
    private void assignRoleToUser(RealmResource realmResource,
                                  UserResource userResource,
                                  String roleName,
                                  String username) {
        try {
            // Vérifie que le rôle existe dans Keycloak
            RoleRepresentation role = realmResource.roles()
                    .get(roleName)
                    .toRepresentation();

            userResource.roles().realmLevel().add(Collections.singletonList(role));

            log.info("✅ Role '{}' assigned to user: {}", roleName, username);

        } catch (Exception e) {
            // ⚠️ Le rôle n'existe pas dans Keycloak → exception claire
            // Ne pas avaler silencieusement cette erreur
            log.error("❌ ERREUR : Le rôle '{}' n'existe pas dans le realm '{}'. "
                            + "Créez-le dans Keycloak Admin Console : "
                            + "Realm roles → Create role → '{}'",
                    roleName, realm, roleName);

            throw new RuntimeException(
                    "Le rôle '" + roleName + "' est introuvable dans Keycloak. "
                            + "Veuillez le créer dans Keycloak Admin Console avant de relancer."
            );
        }
    }

    // ══════════════════════════════════════════════════════════════
    // FORGOT PASSWORD
    // ══════════════════════════════════════════════════════════════
    public void sendPasswordResetEmail(String email) {
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
            usersResource.get(userId).executeActionsEmail(Arrays.asList("UPDATE_PASSWORD"));

            log.info("✅ Password reset email sent to: {}", email);

        } finally {
            adminClient.close();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // SEARCH USERS
    // ══════════════════════════════════════════════════════════════
    public List<UserInfo> searchUsers(String query) {
        Keycloak adminClient = getKeycloakInstance();

        try {
            return adminClient.realm(realm)
                    .users()
                    .search(query, 0, 10)
                    .stream()
                    .map(this::mapToUserInfo)
                    .collect(Collectors.toList());
        } finally {
            adminClient.close();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // GET USER BY ID
    // ══════════════════════════════════════════════════════════════
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

    // ══════════════════════════════════════════════════════════════
    // GET ALL USERS (admin only)
    // ══════════════════════════════════════════════════════════════
    public List<UserInfo> getAllUsers() {
        Keycloak adminClient = getKeycloakInstance();

        try {
            return adminClient.realm(realm)
                    .users()
                    .list()
                    .stream()
                    .map(this::mapToUserInfo)
                    .collect(Collectors.toList());
        } finally {
            adminClient.close();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════
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