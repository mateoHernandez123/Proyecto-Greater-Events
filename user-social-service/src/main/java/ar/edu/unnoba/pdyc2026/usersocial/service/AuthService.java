package ar.edu.unnoba.pdyc2026.usersocial.service;

import ar.edu.unnoba.pdyc2026.common.exception.BusinessRuleException;
import ar.edu.unnoba.pdyc2026.usersocial.config.KeycloakAdminProperties;
import ar.edu.unnoba.pdyc2026.usersocial.dto.RegisterUserRequest;
import ar.edu.unnoba.pdyc2026.usersocial.dto.RegisterUserResponse;
import ar.edu.unnoba.pdyc2026.usersocial.model.User;
import ar.edu.unnoba.pdyc2026.usersocial.repository.UserRepository;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final Keycloak keycloak;
    private final String realm;
    private final Executor keycloakExecutor;
    private final KeycloakRoleService roleService;
    private final UserRepository userRepository;

    public AuthService(
            Keycloak keycloak,
            KeycloakAdminProperties properties,
            @Qualifier("keycloakExecutor") Executor keycloakExecutor,
            KeycloakRoleService roleService,
            UserRepository userRepository) {
        this.keycloak = keycloak;
        this.realm = properties.realm();
        this.keycloakExecutor = keycloakExecutor;
        this.roleService = roleService;
        this.userRepository = userRepository;
    }

    public CompletableFuture<RegisterUserResponse> register(RegisterUserRequest request) {
        return CompletableFuture.supplyAsync(() -> registerBlocking(request), keycloakExecutor);
    }

    private RegisterUserResponse registerBlocking(RegisterUserRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByUsername(username)) {
            throw new BusinessRuleException("Username already in use: " + username);
        }
        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("Email already in use: " + email);
        }

        UserRepresentation kcUser = new UserRepresentation();
        kcUser.setUsername(username);
        kcUser.setEmail(email);
        kcUser.setFirstName(request.firstName());
        kcUser.setLastName(request.lastName());
        kcUser.setEnabled(true);
        kcUser.setEmailVerified(true);

        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setValue(request.password());
        cred.setTemporary(false);
        kcUser.setCredentials(List.of(cred));

        UsersResource users = keycloak.realm(realm).users();
        String keycloakId;
        try (Response response = users.create(kcUser)) {
            if (response.getStatus() == Response.Status.CONFLICT.getStatusCode()) {
                throw new BusinessRuleException("User already exists in Keycloak: " + username);
            }
            if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
                throw new BusinessRuleException(
                        "Keycloak rejected registration: HTTP " + response.getStatus());
            }
            keycloakId = CreatedResponseUtil.getCreatedId(response);
        }

        try {
            roleService.assignRealmRole(keycloakId, KeycloakRoleService.USER_ROLE);
            return persistLocal(keycloakId, username, email);
        } catch (RuntimeException ex) {
            rollbackKeycloak(users, keycloakId);
            throw ex;
        }
    }

    private RegisterUserResponse persistLocal(String keycloakId, String username, String email) {
        User user = new User();
        user.setKeycloakId(keycloakId);
        user.setUsername(username);
        user.setEmail(email);
        user.setCreatedAt(Instant.now());
        User saved = userRepository.save(user);
        return new RegisterUserResponse(saved.getId(), saved.getKeycloakId(), saved.getUsername(), saved.getEmail());
    }

    private static void rollbackKeycloak(UsersResource users, String keycloakId) {
        try {
            users.get(keycloakId).remove();
        } catch (NotFoundException ignored) {
            // already removed
        }
    }
}
