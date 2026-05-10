package ar.edu.unnoba.pdyc2026.events.service;

import ar.edu.unnoba.pdyc2026.events.config.KeycloakAdminProperties;
import ar.edu.unnoba.pdyc2026.events.dto.AdminUserCreateRequest;
import ar.edu.unnoba.pdyc2026.events.dto.AdminUserResponse;
import ar.edu.unnoba.pdyc2026.events.exception.BusinessRuleException;
import ar.edu.unnoba.pdyc2026.events.exception.ResourceNotFoundException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
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
public class AdminUserService {

    private final Keycloak keycloak;
    private final String realm;
    private final Executor keycloakExecutor;

    public AdminUserService(
            Keycloak keycloak,
            KeycloakAdminProperties properties,
            @Qualifier("keycloakExecutor") Executor keycloakExecutor) {
        this.keycloak = keycloak;
        this.realm = properties.realm();
        this.keycloakExecutor = keycloakExecutor;
    }

    public CompletableFuture<List<AdminUserResponse>> listUsers() {
        return CompletableFuture.supplyAsync(
                () -> usersResource().list().stream().map(this::toResponse).toList(), keycloakExecutor);
    }

    public CompletableFuture<AdminUserResponse> getUser(String id) {
        return CompletableFuture.supplyAsync(() -> toResponse(findUser(id)), keycloakExecutor);
    }

    public CompletableFuture<AdminUserResponse> createUser(AdminUserCreateRequest request) {
        return CompletableFuture.supplyAsync(() -> createUserBlocking(request), keycloakExecutor);
    }

    public CompletableFuture<Void> deleteUser(String id) {
        return CompletableFuture.runAsync(
                () -> {
                    try {
                        usersResource().get(id).remove();
                    } catch (NotFoundException ex) {
                        throw new ResourceNotFoundException("Admin user not found: " + id);
                    }
                },
                keycloakExecutor);
    }

    private AdminUserResponse createUserBlocking(AdminUserCreateRequest request) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEnabled(request.enabled() == null || request.enabled());
        user.setCredentials(List.of(passwordCredential(request)));

        try (Response response = usersResource().create(user)) {
            if (response.getStatus() == Response.Status.CONFLICT.getStatusCode()) {
                throw new BusinessRuleException("Admin user already exists: " + request.username());
            }
            if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
                throw new BusinessRuleException("Keycloak rejected user creation: HTTP " + response.getStatus());
            }
            return toResponse(findUser(CreatedResponseUtil.getCreatedId(response)));
        }
    }

    private CredentialRepresentation passwordCredential(AdminUserCreateRequest request) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.password());
        credential.setTemporary(Boolean.TRUE.equals(request.temporaryPassword()));
        return credential;
    }

    private UserRepresentation findUser(String id) {
        try {
            return usersResource().get(id).toRepresentation();
        } catch (NotFoundException ex) {
            throw new ResourceNotFoundException("Admin user not found: " + id);
        }
    }

    private UsersResource usersResource() {
        return keycloak.realm(realm).users();
    }

    private AdminUserResponse toResponse(UserRepresentation user) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.isEnabled());
    }
}
