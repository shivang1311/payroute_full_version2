package com.payroute.iam.service;

import com.payroute.iam.entity.Role;
import com.payroute.iam.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtService}. Builds the service directly with a
 * controlled secret + expirations rather than relying on Spring autowiring.
 */
class JwtServiceTest {

    /** 32+ char secret — HmacShaKeyFor needs at least 256 bits / 32 bytes for HS256. */
    private static final String SECRET =
            "unit-test-secret-do-not-use-in-production-32+chars";

    private JwtService jwt;
    private User user;

    @BeforeEach
    void setUp() {
        jwt = new JwtService(SECRET, 60_000L, 600_000L);
        user = User.builder()
                .id(42L)
                .username("alice")
                .email("alice@example.com")
                .role(Role.CUSTOMER)
                .partyId(101L)
                .build();
    }

    @DisplayName("generateAccessToken includes id/username/role/partyId claims")
    @Test
    void generateAccessToken_includesAllClaims() {
        String token = jwt.generateAccessToken(user);
        Claims claims = jwt.validateToken(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("username", String.class)).isEqualTo("alice");
        assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");
        assertThat(((Number) claims.get("partyId")).longValue()).isEqualTo(101L);
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @DisplayName("generateAccessToken omits partyId when null (e.g. staff)")
    @Test
    void generateAccessToken_omitsPartyIdWhenNull() {
        user.setPartyId(null);
        Claims claims = jwt.validateToken(jwt.generateAccessToken(user));
        assertThat(claims.get("partyId")).isNull();
    }

    @DisplayName("generateRefreshToken returns a valid UUID string")
    @Test
    void generateRefreshToken_isUuid() {
        String tok = jwt.generateRefreshToken();
        // UUID.fromString throws on bad input — this assertion implicitly validates format.
        UUID parsed = UUID.fromString(tok);
        assertThat(parsed.toString()).isEqualTo(tok);
    }

    @DisplayName("validateToken accepts a freshly-issued token")
    @Test
    void validateToken_validToken() {
        String token = jwt.generateAccessToken(user);
        Claims claims = jwt.validateToken(token);
        assertThat(claims.getSubject()).isEqualTo("42");
    }

    @DisplayName("validateToken rejects a token signed with a different secret")
    @Test
    void validateToken_rejectsForeignSignature() {
        JwtService imposter = new JwtService(
                "different-secret-32-chars-pls-trust-me-bro-padding",
                60_000L, 600_000L);
        String foreignToken = imposter.generateAccessToken(user);

        assertThatThrownBy(() -> jwt.validateToken(foreignToken))
                .isInstanceOf(SignatureException.class);
    }

    @DisplayName("validateToken rejects an expired token")
    @Test
    void validateToken_rejectsExpired() throws InterruptedException {
        // Build a service with a 1ms access-token TTL so the token expires immediately.
        JwtService shortLived = new JwtService(SECRET, 1L, 600_000L);
        String token = shortLived.generateAccessToken(user);
        Thread.sleep(10);

        assertThatThrownBy(() -> shortLived.validateToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @DisplayName("validateToken rejects garbage input")
    @Test
    void validateToken_rejectsGarbage() {
        assertThatThrownBy(() -> jwt.validateToken("not.a.real.jwt"))
                .isInstanceOf(MalformedJwtException.class);
    }

    @DisplayName("getUserIdFromToken parses the subject as a Long")
    @Test
    void getUserIdFromToken_parsesSubject() {
        String token = jwt.generateAccessToken(user);
        assertThat(jwt.getUserIdFromToken(token)).isEqualTo(42L);
    }

    @DisplayName("expiration getters return the values configured at construction")
    @Test
    void expirationAccessors() {
        assertThat(jwt.getAccessTokenExpirationMs()).isEqualTo(60_000L);
        assertThat(jwt.getRefreshTokenExpirationMs()).isEqualTo(600_000L);
    }
}
