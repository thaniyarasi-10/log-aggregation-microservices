package com.kovanlabs.servicemanagementservice.controller;

import java.security.Principal;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kovanlabs.servicemanagementservice.dto.auth.AuthResponse;
import com.kovanlabs.servicemanagementservice.dto.auth.LoginRequest;
import com.kovanlabs.servicemanagementservice.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.loginWithEmail(request.email(), request.displayName()));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(
            @RequestHeader(value = "X-User-Email", required = false) String proxiedUserEmail,
            Principal principal) {
        String email = proxiedUserEmail;
        if ((email == null || email.isBlank()) && principal != null) {
            email = principal.getName();
        }
        return ResponseEntity.ok(authService.getCurrentUserContext(email));
    }
}
