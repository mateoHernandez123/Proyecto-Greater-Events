package ar.edu.unnoba.pdyc2026.notification.config;

import ar.edu.unnoba.pdyc2026.notification.client.UserSocialClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;

@TestConfiguration
public class TestFeignConfig {

    @MockBean
    UserSocialClient userSocialClient;
}
