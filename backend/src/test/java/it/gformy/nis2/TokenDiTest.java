package it.gformy.nis2;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Nei test Keycloak non c'e': il backend riceve token firmati con una chiave
 * generata qui, con la stessa forma di quelli veri (preferred_username, realm_access.roles).
 * Si sostituisce solo chi verifica la firma; il resto della catena di sicurezza e' quello reale.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TokenDiTest {

    private static final RSAKey CHIAVE = generaChiave();

    @Bean
    JwtDecoder jwtDecoder() throws JOSEException {
        return NimbusJwtDecoder.withPublicKey(CHIAVE.toRSAPublicKey()).build();
    }

    public static String token(String utente, String... ruoli) {
        Instant adesso = Instant.now();
        JWTClaimsSet contenuto = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .claim("preferred_username", utente)
                .claim("realm_access", Map.of("roles", List.of(ruoli)))
                .issueTime(Date.from(adesso))
                .expirationTime(Date.from(adesso.plusSeconds(300)))
                .build();
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(CHIAVE.getKeyID()).build(), contenuto);
            jwt.sign(new RSASSASigner(CHIAVE));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey generaChiave() {
        try {
            return new RSAKeyGenerator(2048).keyID("chiave-di-test").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
