package ar.edu.unnoba.pdyc2026.usersocial;

import ar.edu.unnoba.pdyc2026.usersocial.client.CatalogClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class UserSocialServiceApplicationTests {

    @MockBean
    private CatalogClient catalogClient;

    @Test
    void contextLoads() {}
}
