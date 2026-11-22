package tn.ginf.smarthealthcare.gateway.securite;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Sécurité de la gateway (P3.4, D41, D44) : jeton JWT HS256 émis par {@code POST /auth/login} et exigé sur toutes
 * les routes des services ; restent publics le login, les outils de test (WSDL, Swagger UI, GraphiQL) et les pages
 * du client web (D51). Les rôles sont portés par le jeton ; l'accès aux écrans par rôle est géré par le client (D48).
 */
@Configuration
@EnableConfigurationProperties(SecuriteProperties.class)
public class ConfigurationSecurite {

    /** Seul chemin où le jeton est accepté en paramètre d'URL : EventSource (SSE) ne peut pas envoyer d'en-tête. */
    static final String CHEMIN_SSE = "/events";

    /** Pages et fichiers statiques du client web (D51), publics en GET : ils se chargent avant la connexion. */
    static final String[] CHEMINS_CLIENT_WEB = {"/", "/index.html", "/*.js", "/*.css", "/*.ico", "/seed/**",
            "/connexion", "/patient", "/patient/**", "/medecin", "/medecin/**", "/admin", "/admin/**"};

    @Bean
    SecurityWebFilterChain chaineSecurite(ServerHttpSecurity http, ReactiveJwtDecoder decodeur) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(regles -> regles
                        .pathMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        // Outils de test publics (D44) : WSDL/XSD pour SoapUI, contrat et Swagger UI, page GraphiQL
                        .pathMatchers(HttpMethod.GET, "/ws/dossier", "/ws/dossier/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**", "/openapi/**",
                                "/v3/api-docs", "/v3/api-docs/**", "/graphiql", "/graphiql/**").permitAll()
                        // Client web (D51)
                        .pathMatchers(HttpMethod.GET, CHEMINS_CLIENT_WEB).permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .bearerTokenConverter(convertisseurJeton())
                        .jwt(jwt -> jwt
                                .jwtDecoder(decodeur)
                                .jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(
                                        ConfigurationSecurite::authentification))))
                .build();
    }

    /** En-tête {@code Authorization: Bearer …} partout ; {@code ?access_token=…} accepté en plus sur /events. */
    static ServerAuthenticationConverter convertisseurJeton() {
        ServerBearerTokenAuthenticationConverter entete = new ServerBearerTokenAuthenticationConverter();
        ServerBearerTokenAuthenticationConverter enteteOuParametre = new ServerBearerTokenAuthenticationConverter();
        enteteOuParametre.setAllowUriQueryParameter(true);
        return echange -> CHEMIN_SSE.equals(echange.getRequest().getPath().pathWithinApplication().value())
                ? enteteOuParametre.convert(echange)
                : entete.convert(echange);
    }

    /** Le rôle du jeton devient l'autorité ROLE_xxx ; le nom de l'utilisateur est le sujet (TN-…, MED-…, admin). */
    static AbstractAuthenticationToken authentification(Jwt jwt) {
        String role = jwt.getClaimAsString(ServiceJeton.CLAIM_ROLE);
        var autorites = role == null ? List.<SimpleGrantedAuthority>of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
        return new JwtAuthenticationToken(jwt, autorites, jwt.getSubject());
    }

    @Bean
    SecretKey cleJwt(SecuriteProperties proprietes) {
        byte[] octets = proprietes.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (octets.length < 32) {
            throw new IllegalStateException("securite.jwt.secret doit faire au moins 32 octets (HS256)");
        }
        return new SecretKeySpec(octets, "HmacSHA256");
    }

    @Bean
    JwtEncoder encodeurJwt(SecretKey cle) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(cle));
    }

    @Bean
    ReactiveJwtDecoder decodeurJwt(SecretKey cle, SecuriteProperties proprietes) {
        NimbusReactiveJwtDecoder decodeur = NimbusReactiveJwtDecoder.withSecretKey(cle)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // expiration (avec la tolérance par défaut) + émetteur = la gateway
        decodeur.setJwtValidator(JwtValidators.createDefaultWithIssuer(proprietes.jwt().emetteur()));
        return decodeur;
    }
}
