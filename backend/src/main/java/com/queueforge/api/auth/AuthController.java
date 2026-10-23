package com.queueforge.api.auth;

import com.queueforge.api.config.AppPrincipal;
import com.queueforge.api.config.JwtService;
import com.queueforge.api.user.AppUser;
import com.queueforge.api.user.AppUserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AppUserRepository users, PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        AppUser user = users.save(new AppUser(request.name(), request.email(), passwordEncoder.encode(request.password())));
        return response(user);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        AppUser user = users.findByEmailIgnoreCase(request.email()).orElseThrow();
        return response(user);
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal AppPrincipal principal) {
        return Map.of("id", principal.id(), "name", principal.name(), "email", principal.email());
    }

    private AuthResponse response(AppUser user) {
        return new AuthResponse(jwtService.issue(user), new UserView(user.getId().toString(), user.getName(), user.getEmail()));
    }

    public record RegisterRequest(@NotBlank @Size(min = 2, max = 120) String name,
                                  @NotBlank @Email String email,
                                  @NotBlank @Size(min = 8, max = 72) String password) {}
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record UserView(String id, String name, String email) {}
    public record AuthResponse(String token, UserView user) {}
}

