package com.kovanlabs.servicemanagementservice.security;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.kovanlabs.servicemanagementservice.dto.auth.AuthResponse;
import com.kovanlabs.servicemanagementservice.service.AuthService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final String redirectBase;

    public OAuth2AuthenticationSuccessHandler(
            AuthService authService,
            @Value("${app.auth.oauth-success-redirect}") String redirectBase) {
        this.authService = authService;
        this.redirectBase = redirectBase;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String email = readAttribute(oauth2User, "email", "preferred_username", "upn", "sub");
        String name = readAttribute(oauth2User, "name", "preferred_username", "sub");

        AuthResponse auth = authService.loginFromFederatedIdentity(email, name);
        String redirect = redirectBase
                + "?token=" + URLEncoder.encode(auth.accessToken(), StandardCharsets.UTF_8)
                + "&expiresAt=" + auth.expiresAtEpochSeconds();

        response.sendRedirect(redirect);
    }

    private String readAttribute(OAuth2User user, String... keys) {
        for (String key : keys) {
            Object value = user.getAttributes().get(key);
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                return stringValue;
            }
        }
        return "unknown@local";
    }
}
