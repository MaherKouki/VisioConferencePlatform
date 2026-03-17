package com.example.auth_service.controller;

import com.example.auth_service.dto.CreateGroupRequest;
import com.example.auth_service.dto.GroupResponse;
import com.example.auth_service.entity.GroupMember;
import com.example.auth_service.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    // ═══════════════════════════════════════════════════════════
    // POURQUOI ROLE_USER pour tout GroupController ?
    //
    // Les groupes sont une fonctionnalité MÉTIER pour les users :
    //   - Créer un groupe       → any user peut créer son propre groupe
    //   - Voir ses groupes      → any user voit ses propres groupes
    //   - Ajouter un membre     → géré par la logique OWNER/ADMIN en service
    //   - Supprimer un membre   → géré par la logique OWNER/ADMIN en service
    //
    // La granularité fine (OWNER/ADMIN/MEMBER) est gérée dans GroupService,
    // pas au niveau HTTP — Spring Security protège l'accès global,
    // la logique métier protège l'accès fin.
    //
    // ROLE_ADMIN plateforme n'a pas besoin de gérer les groupes des users
    // → pas de @PreAuthorize("hasRole('ROLE_ADMIN')") ici
    // ═══════════════════════════════════════════════════════════

    /**
     * POST /api/groups
     * Créer un groupe — tout utilisateur connecté peut créer un groupe
     * Il devient automatiquement OWNER du groupe (logique dans GroupService)
     *
     * ROLE_USER ✅ — c'est une action utilisateur normale
     * ROLE_ADMIN ✅ — un admin est aussi un user, il peut créer des groupes
     */
    @PostMapping
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<GroupResponse> createGroup(
            @RequestBody CreateGroupRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        GroupResponse group = groupService.createGroup(request, userId);
        return ResponseEntity.ok(group);
    }

    /**
     * GET /api/groups
     * Voir mes groupes — chaque user voit uniquement ses propres groupes
     *
     * ROLE_USER ✅ — consultation personnelle
     */
    @GetMapping
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<List<GroupResponse>> getMyGroups(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<GroupResponse> groups = groupService.getMyGroups(userId);
        return ResponseEntity.ok(groups);
    }

    /**
     * GET /api/groups/{id}
     * Voir un groupe spécifique
     *
     * ROLE_USER ✅ — un membre peut consulter son groupe
     * Note : idéalement vérifier que l'user est membre du groupe dans le service
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<GroupResponse> getGroup(@PathVariable Long id) {
        GroupResponse group = groupService.getGroup(id);
        return ResponseEntity.ok(group);
    }

    /**
     * POST /api/groups/{groupId}/members/{userId}
     * Ajouter un membre à un groupe
     *
     * ROLE_USER ✅ — l'endpoint est accessible à tout user connecté
     * ⚠️ La vraie protection est dans GroupService.addMember() :
     *    → vérifie que le demandeur est OWNER ou ADMIN du groupe
     *    → un simple MEMBER ne peut pas ajouter quelqu'un
     */
    @PostMapping("/{groupId}/members/{userId}")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> addMember(
            @PathVariable Long groupId,
            @PathVariable String userId,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {
        String role = body != null ? body.getOrDefault("role", "MEMBER") : "MEMBER";
        String adminUserId = jwt.getSubject();
        groupService.addMember(groupId, role, userId, adminUserId);
        return ResponseEntity.ok().build();
    }

    /**
     * DELETE /api/groups/{groupId}/members/{userId}
     * Retirer un membre d'un groupe
     *
     * ROLE_USER ✅ — accessible à tout user connecté
     * ⚠️ La vraie protection est dans GroupService.removeMember() :
     *    → vérifie que le demandeur est OWNER ou ADMIN du groupe
     */
    @DeleteMapping("/{groupId}/members/{userId}")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> removeMember(
            @PathVariable Long groupId,
            @PathVariable String userId,
            @AuthenticationPrincipal Jwt jwt) {
        String adminUserId = jwt.getSubject();
        groupService.removeMember(groupId, userId, adminUserId);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /api/groups/{groupId}/members
     * Voir les membres d'un groupe
     *
     * ROLE_USER ✅ — un membre peut voir qui est dans son groupe
     * Note : idéalement vérifier que le demandeur est membre du groupe
     */
    @GetMapping("/{groupId}/members")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> getGroupMembers(
            @PathVariable Long groupId,
            @AuthenticationPrincipal Jwt jwt) {
        try {
            List<GroupMember> members = groupService.getGroupMembers(groupId);
            return ResponseEntity.ok(members);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}