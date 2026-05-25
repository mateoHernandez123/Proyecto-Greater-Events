package ar.edu.unnoba.pdyc2026.events.web;

import ar.edu.unnoba.pdyc2026.events.dto.AdminUserCreateRequest;
import ar.edu.unnoba.pdyc2026.events.dto.AdminUserResponse;
import ar.edu.unnoba.pdyc2026.events.service.AdminUserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public CompletableFuture<List<AdminUserResponse>> listUsers() {
        return adminUserService.listUsers();
    }

    @GetMapping("/{id}")
    public CompletableFuture<AdminUserResponse> getUser(@PathVariable String id) {
        return adminUserService.getUser(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompletableFuture<AdminUserResponse> createUser(
            @Valid @RequestBody AdminUserCreateRequest request) {
        return adminUserService.createUser(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public CompletableFuture<Void> deleteUser(@PathVariable String id) {
        return adminUserService.deleteUser(id);
    }
}
