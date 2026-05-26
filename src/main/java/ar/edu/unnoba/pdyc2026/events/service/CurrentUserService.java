package ar.edu.unnoba.pdyc2026.events.service;

import ar.edu.unnoba.pdyc2026.events.exception.BusinessRuleException;
import ar.edu.unnoba.pdyc2026.events.model.User;
import ar.edu.unnoba.pdyc2026.events.repository.UserRepository;
import java.time.Instant;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve el {@link User} local asociado al token JWT del request en curso (TP4).
 *
 * <p>Si el usuario autenticado por Keycloak todavia no existe en la base local
 * (por ejemplo, porque se creo directamente en Keycloak por consola o porque
 * el registro local fallo luego de crearlo en Keycloak), se aprovisiona
 * automaticamente leyendo {@code sub}, {@code preferred_username} y {@code email}
 * del propio token. Asi se mantiene la coherencia sin pedirle al cliente que
 * vuelva a registrarse.
 */
@Service
public class CurrentUserService {

    private static final String CLAIM_PREFERRED_USERNAME = "preferred_username";
    private static final String CLAIM_EMAIL = "email";

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Devuelve el usuario local correspondiente al JWT actual. Si no existe lo
     * persiste (auto-provisioning).
     */
    @Transactional
    public User getOrProvisionCurrentUser() {
        Jwt jwt = requireJwt();
        String keycloakId = jwt.getSubject();
        return userRepository
                .findByKeycloakId(keycloakId)
                .orElseGet(() -> provisionFromJwt(jwt));
    }

    /**
     * Misma logica que {@link #getOrProvisionCurrentUser()} pero trayendo eager la coleccion
     * de artistas seguidos para evitar N+1 en los endpoints {@code /me/following*}.
     */
    @Transactional
    public User getOrProvisionWithFollowing() {
        User user = getOrProvisionCurrentUser();
        return userRepository
                .findWithFollowingArtistsByKeycloakId(user.getKeycloakId())
                .orElse(user);
    }

    /**
     * Misma logica que {@link #getOrProvisionCurrentUser()} pero trayendo eager la coleccion
     * de eventos favoritos.
     */
    @Transactional
    public User getOrProvisionWithFavorites() {
        User user = getOrProvisionCurrentUser();
        return userRepository
                .findWithFavoriteEventsByKeycloakId(user.getKeycloakId())
                .orElse(user);
    }

    private User provisionFromJwt(Jwt jwt) {
        String keycloakId = jwt.getSubject();
        String username = jwt.getClaimAsString(CLAIM_PREFERRED_USERNAME);
        String email = jwt.getClaimAsString(CLAIM_EMAIL);
        if (username == null || username.isBlank()) {
            throw new BusinessRuleException(
                    "JWT does not contain preferred_username; cannot provision local user.");
        }
        if (email == null || email.isBlank()) {
            throw new BusinessRuleException(
                    "JWT does not contain an email claim; cannot provision local user.");
        }
        User user = new User();
        user.setKeycloakId(keycloakId);
        user.setUsername(username);
        user.setEmail(email);
        user.setCreatedAt(Instant.now());
        return userRepository.save(user);
    }

    private static Jwt requireJwt() {
        Object auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        throw new BusinessRuleException("Authentication context is not a JWT.");
    }
}
