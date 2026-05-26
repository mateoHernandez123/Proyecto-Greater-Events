package ar.edu.unnoba.pdyc2026.events.config;

import java.util.concurrent.Executor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuracion central de seguridad (TP3 + TP4).
 *
 * <p>TP4: el filter chain expone tres niveles claros:
 * <ul>
 *   <li>Publicos: {@code POST /auth/register}, {@code GET /artists/**}, {@code GET /events/**}.</li>
 *   <li>Administradores: {@code /admin/**} exige rol de realm {@code admin}.</li>
 *   <li>Usuarios finales autenticados: {@code /me/**} exige token valido (rol {@code user}).</li>
 * </ul>
 *
 * <p>Los roles se extraen del JWT en {@link KeycloakJwtAuthenticationConverter} (claim
 * {@code realm_access.roles}) y se exponen como {@code ROLE_admin} / {@code ROLE_user}.
 */
@Configuration
@EnableWebSecurity
@EnableAsync
@EnableConfigurationProperties(KeycloakAdminProperties.class)
public class SecurityConfig {

    private final JsonAuthEntryPoint authEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;
    private final KeycloakJwtAuthenticationConverter jwtAuthenticationConverter;

    public SecurityConfig(
            JsonAuthEntryPoint authEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler,
            KeycloakJwtAuthenticationConverter jwtAuthenticationConverter) {
        this.authEntryPoint = authEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth
                                        // Endpoints publicos (TP4): registro y catalogo abierto.
                                        .requestMatchers(HttpMethod.POST, "/auth/register").permitAll()
                                        .requestMatchers(HttpMethod.GET, "/artists", "/artists/**").permitAll()
                                        .requestMatchers(HttpMethod.GET, "/events", "/events/**").permitAll()
                                        .requestMatchers("/error").permitAll()
                                        // Backoffice: solo admins.
                                        .requestMatchers("/admin/**").hasRole("admin")
                                        // Acciones de usuario final autenticado.
                                        .requestMatchers("/me/**").hasRole("user")
                                        .anyRequest().authenticated())
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                                        .authenticationEntryPoint(authEntryPoint)
                                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(authEntryPoint)
                                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(issuerUri);
        decoder.setJwtValidator(validator);
        return decoder;
    }

    @Bean
    public Keycloak keycloakAdminClient(KeycloakAdminProperties properties) {
        return KeycloakBuilder.builder()
                .serverUrl(properties.serverUrl())
                .realm(properties.realm())
                .clientId(properties.clientId())
                .clientSecret(properties.clientSecret())
                .grantType(properties.grantType())
                .build();
    }

    @Bean
    public Executor keycloakExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("keycloak-admin-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("notifications-");
        executor.initialize();
        return executor;
    }
}
