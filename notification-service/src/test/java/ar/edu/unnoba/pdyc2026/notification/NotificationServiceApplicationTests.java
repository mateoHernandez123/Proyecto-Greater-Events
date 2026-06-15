package ar.edu.unnoba.pdyc2026.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(ar.edu.unnoba.pdyc2026.notification.config.TestFeignConfig.class)
class NotificationServiceApplicationTests {

    @Test
    void contextLoads() {}
}
