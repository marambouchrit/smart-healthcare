package tn.ginf.smarthealthcare.graphql;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * PostgreSQL jetable pour les tests (Testcontainers, même image que docker-compose.yml), un seul conteneur pour
 * toute la série. Les classes de test héritent de {@link PostgresTest.BaseVide} : les données écrites par
 * l'orchestrateur sont effacées avant chaque test (le référentiel du seed reste).
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

    public abstract static class BaseVide {
        @Autowired protected JdbcTemplate jdbc;

        @BeforeEach
        void viderLesDonneesDeLOrchestrateur() {
            jdbc.execute("TRUNCATE fiche_urgence, alerte, constante, couverture RESTART IDENTITY");
        }
    }
}
