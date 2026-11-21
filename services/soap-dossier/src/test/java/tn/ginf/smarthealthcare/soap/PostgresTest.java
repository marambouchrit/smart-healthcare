package tn.ginf.smarthealthcare.soap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * PostgreSQL jetable pour les tests (Testcontainers, même image que docker-compose.yml).
 * Un seul conteneur pour toute la série de tests ; @ServiceConnection fournit l'URL, l'utilisateur et le mot de passe.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTest {

    static final PostgreSQLContainer<?> CONTENEUR = new PostgreSQLContainer<>("postgres:18.7-alpine");

    static {
        CONTENEUR.start();
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return CONTENEUR;
    }
}
