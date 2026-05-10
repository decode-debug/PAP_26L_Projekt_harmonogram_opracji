package pl.pw.elka.scheduleapp.security;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

@Service
public class JwtService {

    private static final long EXPIRATION_MS = 15L * 60 * 1000; // 15 minutes

    private final SecretKey signingKey;

    public JwtService() {
        // Generate a secure random HS256 key at startup using the modern JJWT 0.12 API
        this.signingKey = Jwts.SIG.HS256.key().build();
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
