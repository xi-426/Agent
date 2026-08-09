package com.yan.agent.auth;

import com.yan.agent.user.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class JwtService {
    private final JwtEncoder jwtEncoder;
    private final Duration tokenTtl;

    public JwtService(JwtEncoder jwtEncoder,
            @Value("${app.security.jwt-ttl-minutes}") long ttlMinutes) {
        this.jwtEncoder = jwtEncoder;
        this.tokenTtl = Duration.ofMinutes(ttlMinutes);
    }

    public String createToken(AppUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("enterprise-agent")
                .issuedAt(now)
                .expiresAt(now.plus(tokenTtl))
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("displayName", user.getDisplayName())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims))
                .getTokenValue();
    }

    public long getExpiresInSeconds() {
        return tokenTtl.toSeconds();
    }
}
