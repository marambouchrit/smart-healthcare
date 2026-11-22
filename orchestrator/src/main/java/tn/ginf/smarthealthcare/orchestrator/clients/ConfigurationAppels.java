package tn.ginf.smarthealthcare.orchestrator.clients;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * Délais d'attente des appels REST et GraphQL (P5.3, D54) : appliqués au RestClient.Builder de Spring Boot,
 * donc à {@link ClientPortailRest} et {@link ClientTableauDeBordGraphql}. Le client SOAP règle les mêmes délais
 * sur son conduit CXF ({@link ClientDossierSoap}).
 */
@Configuration
public class ConfigurationAppels {

    @Bean
    RestClientCustomizer delaisDAttente(@Value("${orchestrateur.appels.delai-connexion}") Duration connexion,
                                        @Value("${orchestrateur.appels.delai-reponse}") Duration reponse) {
        return builder -> builder.requestFactory(fabrique(connexion, reponse));
    }

    static JdkClientHttpRequestFactory fabrique(Duration connexion, Duration reponse) {
        JdkClientHttpRequestFactory fabrique = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(connexion).build());
        fabrique.setReadTimeout(reponse);
        return fabrique;
    }
}
