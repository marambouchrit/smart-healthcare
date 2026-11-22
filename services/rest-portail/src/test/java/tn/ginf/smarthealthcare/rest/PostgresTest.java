package tn.ginf.smarthealthcare.rest;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import tn.ginf.smarthealthcare.rest.seed.SeedRepository;

/**
 * PostgreSQL jetable pour les tests (Testcontainers, même image que docker-compose.yml), un seul conteneur pour
 * toute la série. Les classes de test héritent de {@link PostgresTest.BaseVide} : avant chaque test, les rendez-vous
 * sont effacés, les profils remis à leur valeur du seed et l'horloge de test remise au mardi 2026-11-17 10h10.
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
        @Autowired protected SeedRepository seed;
        @Autowired protected HorlogeTest horloge;

        @BeforeEach
        void repartirDUneBasePropre() {
            jdbc.execute("TRUNCATE rendez_vous, intervention_ambulance RESTART IDENTITY");
            seed.restaurerProfils();
            horloge.regler(17, 10, 10);
        }
    }
}
