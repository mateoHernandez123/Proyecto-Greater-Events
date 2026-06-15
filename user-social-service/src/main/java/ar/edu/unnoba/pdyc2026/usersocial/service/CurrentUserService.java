package ar.edu.unnoba.pdyc2026.usersocial.service;

import ar.edu.unnoba.pdyc2026.common.exception.BusinessRuleException;
import ar.edu.unnoba.pdyc2026.usersocial.model.User;
import ar.edu.unnoba.pdyc2026.usersocial.repository.UserRepository;
import java.time.Instant;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {

    private static final String CLAIM_PREFERRED_USERNAME = "preferred_username";
    private static final String CLAIM_EMAIL = "email";

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User getOrProvisionCurrentUser() {
        Jwt jwt = requireJwt();
        String keycloakId = jwt.getSubject();
        return userRepository
                .findByKeycloakId(keycloakId)
                .orElseGet(() -> provisionFromJwt(jwt));
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
