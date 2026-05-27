package com.kovanlabs.servicemanagementservice.security;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@Service
public class JwtTokenService {

    private final SecretKey key;
    private final String issuer;
    private final long expirationSeconds;

    public JwtTokenService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.expiration-seconds}") long expirationSeconds) {
        this.key = new SecretKeySpec(secret.getBytes(), SignatureAlgorithm.HS256.getJcaName());
        this.issuer = issuer;
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(
            String userId,
            String email,
            Collection<String> roles,
            Collection<String> permissions,
            Collection<String> services) {
        long now = System.currentTimeMillis();
        Date issuedAt = new Date(now);
        Date expiresAt = new Date(now + expirationSeconds * 1000);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("email", email);
        claims.put("roles", List.copyOf(roles));
        claims.put("permissions", List.copyOf(permissions));
        claims.put("services", List.copyOf(services));

        return Jwts.builder()
                .issuer(issuer)
                .subject(email)
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .claims(claims)
                .signWith(key)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long expiresAtEpochSeconds() {
        return (System.currentTimeMillis() / 1000) + expirationSeconds;
    }
}
