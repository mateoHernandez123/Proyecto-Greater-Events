package ar.edu.unnoba.pdyc2026.usersocial.service;

import ar.edu.unnoba.pdyc2026.common.exception.BusinessRuleException;
import ar.edu.unnoba.pdyc2026.usersocial.config.KeycloakAdminProperties;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.stereotype.Service;

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
