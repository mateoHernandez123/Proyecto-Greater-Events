package ar.edu.unnoba.pdyc2026.events.web;

import ar.edu.unnoba.pdyc2026.events.dto.RegisterUserRequest;
import ar.edu.unnoba.pdyc2026.events.dto.RegisterUserResponse;
import ar.edu.unnoba.pdyc2026.events.service.AuthService;
import jakarta.validation.Valid;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints publicos de autenticacion / alta de usuarios finales (TP4). */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public CompletableFuture<RegisterUserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        return authService.register(request);
    }
}
