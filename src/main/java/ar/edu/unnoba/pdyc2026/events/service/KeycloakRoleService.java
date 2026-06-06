package ar.edu.unnoba.pdyc2026.events.service;

import ar.edu.unnoba.pdyc2026.events.config.KeycloakAdminProperties;
import ar.edu.unnoba.pdyc2026.events.exception.BusinessRuleException;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.stereotype.Service;

/**
 * Asigna roles de realm a usuarios en Keycloak (TP4). Encapsula el hop bloqueante
 * via admin client, asi {@link AdminUserService} y {@link AuthService} comparten
 * el mismo manejo de errores.
 */
@Service
public class KeycloakRoleService {

    public static final String ADMIN_ROLE = "admin";
    public static final String USER_ROLE = "user";

    private final Keycloak keycloak;
    private final String realm;

    public KeycloakRoleService(Keycloak keycloak, KeycloakAdminProperties properties) {
        this.keycloak = keycloak;
        this.realm = properties.realm();
    }

    /** Asigna {@code roleName} (realm role) al usuario indicado en Keycloak. */
    public void assignRealmRole(String keycloakUserId, String roleName) {
        RealmResource realmResource = keycloak.realm(realm);
        RoleRepresentation role;
        try {
            role = realmResource.roles().get(roleName).toRepresentation();
        } catch (NotFoundException ex) {
            throw new BusinessRuleException(
                    "Realm role '" + roleName + "' not found in Keycloak; run keycloak/setup-realm script.");
        }
        try {
            realmResource.users().get(keycloakUserId).roles().realmLevel().add(List.of(role));
        } catch (NotFoundException ex) {
            throw new BusinessRuleException("Keycloak user not found: " + keycloakUserId);
        }
    }
}
