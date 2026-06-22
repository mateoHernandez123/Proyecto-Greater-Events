package ar.edu.unnoba.pdyc2026.usersocial.service;

import ar.edu.unnoba.pdyc2026.common.dto.NotificationRecipientDto;
import ar.edu.unnoba.pdyc2026.common.messaging.NotificationReason;
import ar.edu.unnoba.pdyc2026.usersocial.model.User;
import ar.edu.unnoba.pdyc2026.usersocial.model.UserFavoriteEvent;
import ar.edu.unnoba.pdyc2026.usersocial.model.UserFollowing;
import ar.edu.unnoba.pdyc2026.usersocial.repository.UserFavoriteEventRepository;
import ar.edu.unnoba.pdyc2026.usersocial.repository.UserFollowingRepository;
import ar.edu.unnoba.pdyc2026.usersocial.repository.UserRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationRecipientService {

    private final UserRepository userRepository;
    private final UserFavoriteEventRepository userFavoriteEventRepository;
    private final UserFollowingRepository userFollowingRepository;

    public NotificationRecipientService(
            UserRepository userRepository,
            UserFavoriteEventRepository userFavoriteEventRepository,
            UserFollowingRepository userFollowingRepository) {
        this.userRepository = userRepository;
        this.userFavoriteEventRepository = userFavoriteEventRepository;
        this.userFollowingRepository = userFollowingRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationRecipientDto> findRecipients(Long eventId, Collection<Long> artistIds) {
        List<NotificationRecipientDto> recipients = new ArrayList<>();
        Set<String> notifiedKeycloakIds = new HashSet<>();

        for (UserFavoriteEvent favorite : userFavoriteEventRepository.findByEventId(eventId)) {
            User user = userRepository.findById(favorite.getUserId()).orElse(null);
            if (user == null) {
                continue;
            }
            recipients.add(new NotificationRecipientDto(user.getKeycloakId(), NotificationReason.FAVORITE_EVENT));
            notifiedKeycloakIds.add(user.getKeycloakId());
        }

        if (artistIds != null && !artistIds.isEmpty()) {
            for (UserFollowing following : userFollowingRepository.findDistinctByArtistIdIn(artistIds)) {
                User user = userRepository.findById(following.getUserId()).orElse(null);
                if (user == null) {
                    continue;
                }
                if (notifiedKeycloakIds.add(user.getKeycloakId())) {
                    recipients.add(
                            new NotificationRecipientDto(user.getKeycloakId(), NotificationReason.FOLLOWED_ARTIST));
                }
            }
        }

        return recipients;
    }
}
