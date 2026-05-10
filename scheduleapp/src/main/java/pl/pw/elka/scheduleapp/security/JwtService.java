package pl.pw.elka.scheduleapp.security;

import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

@Service
public class JwtService {

    private static final long EXPIRATION_MS = 7L * 24 * 60 * 60 * 1000; // 7 days

    private final SecretKey signingKey;

    public JwtService() {
        // Use a persistent secret from the JWT_SECRET env var (Base64-encoded 256-bit key).
        // If not set (dev mode), generate a random key — sessions won't survive restarts.
        String secret = System.getenv("JWT_SECRET");
        if (secret != null && !secret.isBlank()) {
            byte[] keyBytes = Base64.getDecoder().decode(secret.trim());
            this.signingKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        } else {
            this.signingKey = Jwts.SIG.HS256.key().build();
        }
    }

    /** Generates a JWT token with the user's UUID as the subject. */
    public String generateToken(String userUuid) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + EXPIRATION_MS);
        return Jwts.builder()
                .subject(userUuid)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /** Extracts the user UUID from a valid JWT token. */
    public String extractUuid(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /** Returns true if the token is well-formed and not expired. */
    public boolean isValid(String token) {
        try {
            Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
