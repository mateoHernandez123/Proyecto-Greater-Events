package ar.edu.unnoba.pdyc2026.notification.service;

import ar.edu.unnoba.pdyc2026.common.exception.BusinessRuleException;
import ar.edu.unnoba.pdyc2026.notification.model.NotificationUser;
import ar.edu.unnoba.pdyc2026.notification.repository.NotificationUserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {

    private static final String CLAIM_PREFERRED_USERNAME = "preferred_username";
    private static final String CLAIM_EMAIL = "email";

    private final NotificationUserRepository notificationUserRepository;

    public CurrentUserService(NotificationUserRepository notificationUserRepository) {
        this.notificationUserRepository = notificationUserRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationUser getOrProvisionCurrentUser() {
        Jwt jwt = requireJwt();
        String keycloakId = jwt.getSubject();
        return notificationUserRepository
                .findByKeycloakId(keycloakId)
                .orElseGet(() -> provisionFromJwt(jwt));
    }

    private NotificationUser provisionFromJwt(Jwt jwt) {
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
        NotificationUser user = new NotificationUser();
        user.setKeycloakId(keycloakId);
        user.setUsername(username);
        user.setEmail(email);
        return notificationUserRepository.save(user);
    }

    private static Jwt requireJwt() {
        Object auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        throw new BusinessRuleException("Authentication context is not a JWT.");
    }
}
