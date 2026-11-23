package it.gformy.nis2.sicurezza;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Il backend e' un OAuth2 Resource Server: non gestisce password ne' sessioni,
 * accetta solo richieste con un token JWT valido emesso da Keycloak.
 */
@Configuration
@EnableMethodSecurity
public class ConfigurazioneSicurezza {

    @Bean
    SecurityFilterChain catenaDiSicurezza(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(richieste -> richieste
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll())
            .oauth2ResourceServer(server -> server
                .jwt(jwt -> jwt.jwtAuthenticationConverter(convertitoreToken())))
            // Nessuna sessione lato server: ogni richiesta porta il suo token
            .sessionManagement(sessioni -> sessioni.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // CSRF protegge i cookie di sessione; con token Bearer e niente cookie non serve
            .csrf(csrf -> csrf.disable());
        return http.build();
    }

    /**
     * Il nome dell'utente arriva da "preferred_username"; i ruoli del realm
     * Keycloak ("realm_access.roles") diventano ruoli Spring: AMMINISTRATORE -> ROLE_AMMINISTRATORE.
     */
    static JwtAuthenticationConverter convertitoreToken() {
        JwtAuthenticationConverter convertitore = new JwtAuthenticationConverter();
        convertitore.setPrincipalClaimName("preferred_username");
        convertitore.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
            if (realm == null || !(realm.get("roles") instanceof Collection<?> ruoli)) {
                return List.of();
            }
            return ruoli.stream()
                    .map(ruolo -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + ruolo))
                    .toList();
        });
        return convertitore;
    }
}
