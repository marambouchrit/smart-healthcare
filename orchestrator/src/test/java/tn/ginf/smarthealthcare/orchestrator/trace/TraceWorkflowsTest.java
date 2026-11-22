package tn.ginf.smarthealthcare.orchestrator.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.AlerteRecue;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.Droits;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.InterventionAmbulance;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.RendezVousUrgence;
import tn.ginf.smarthealthcare.orchestrator.workflow.ServiceIndisponibleException;
import tn.ginf.smarthealthcare.orchestrator.workflow.WorkflowAlerte;

/**
 * Trace des workflows dans PostgreSQL et lecture par GET /orchestrator/workflows (P5.3, D54), au format de
 * contracts/orchestrateur/workflows-openapi.yaml. PostgreSQL jetable (Testcontainers, même image que docker-compose.yml) ;
 * les services SOAP, REST et GraphQL sont remplacés par de faux services, le workflow et la base sont réels.
 */
@SpringBootTest(properties = {
        // aucun service réel n'est joint pendant ce test : abonnements gRPC sans effet
        "orchestrateur.telemetrie.hote=localhost", "orchestrateur.telemetrie.port=1",
        "orchestrateur.appels.attente-initiale=PT0S"})
@AutoConfigureMockMvc
@Import(TraceWorkflowsTest.Conteneur.class)
class TraceWorkflowsTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Conteneur {
        static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.7-alpine");

        static {
            POSTGRES.start();
        }

        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return POSTGRES;
        }
    }

    static final AlerteRecue ALERTE = new AlerteRecue("ALR-000001", "TN-0012345", "CRITIQUE", "FREQUENCE_CARDIAQUE",
            165, 140, "Fréquence cardiaque 165 bpm > 140 bpm", OffsetDateTime.parse("2026-11-22T17:20:00+01:00"));

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DepotTraces depot;
    @Autowired Ports.Diffusion diffusion;

    int pannesSoap;
    Optional<RendezVousUrgence> rdv = Optional.of(new RendezVousUrgence("RDV-000001", OffsetDateTime.parse("2026-11-22T17:30:00+01:00")));

    @BeforeEach
    void viderLaTrace() {
        jdbc.execute("TRUNCATE workflow, workflow_etape RESTART IDENTITY");
    }

    /** Le vrai workflow, avec la vraie trace et la vraie politique d'essais, sur de faux services. */
    WorkflowAlerte workflow() {
        int[] appelsSoap = {0};
        Ports.Dossier dossier = (p, d) -> {
            if (++appelsSoap[0] <= pannesSoap) throw new ServiceIndisponibleException("SOAP", true, "pas de réponse", null);
            return new Droits("OUVERTS", true, 80, null, "VIG-2026-100001", LocalDate.of(2026, 12, 31));
        };
        Ports.Portail portail = new Ports.Portail() {
            public Optional<RendezVousUrgence> reserverUrgence(AlerteRecue a, boolean r) { return rdv; }
            public InterventionAmbulance envoyerAmbulance(AlerteRecue a, boolean r) {
                return new InterventionAmbulance("AMB-000001", OffsetDateTime.parse("2026-11-22T17:40:00+01:00"));
            }
        };
        Ports.TableauDeBord tdb = new Ports.TableauDeBord() {
            public void enregistrerAlerte(AlerteRecue a) {}
            public void mettreAJourCouverture(String p, Droits d) {}
            public void enregistrerConstantes(String p, List<Ports.ConstanteMesuree> c) {}
            public String creerFicheUrgence(AlerteRecue a, boolean r, String c, RendezVousUrgence v, InterventionAmbulance m) {
                return "FIC-000001";
            }
        };
        return new WorkflowAlerte(dossier, portail, tdb, diffusion, depot, 3, java.time.Duration.ZERO, "Africa/Tunis");
    }

    @Test
    void schemaCreeParFlyway() {
        assertThat(jdbc.queryForObject("SELECT max(version) FROM flyway_schema_history", String.class)).isEqualTo("1");
    }

    @Test
    void workflowTermineEnregistreEtRelu() throws Exception {
        pannesSoap = 1;
        workflow().traiter(ALERTE);

        mvc.perform(get("/orchestrator/workflows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("WF-000001"))
                .andExpect(jsonPath("$[0].alerteId").value("ALR-000001"))
                .andExpect(jsonPath("$[0].patientId").value("TN-0012345"))
                .andExpect(jsonPath("$[0].niveau").value("CRITIQUE"))
                .andExpect(jsonPath("$[0].etat").value("TERMINE"))
                .andExpect(jsonPath("$[0].rendezVousId").value("RDV-000001"))
                .andExpect(jsonPath("$[0].ficheId").value("FIC-000001"))
                .andExpect(jsonPath("$[0].interventionId").doesNotExist())
                .andExpect(jsonPath("$[0].fin").isNotEmpty())
                .andExpect(jsonPath("$[0].dureeMs").isNumber())
                .andExpect(jsonPath("$[0].debut").value(org.hamcrest.Matchers.matchesPattern(
                        "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+01:00")))
                .andExpect(jsonPath("$[0].etapes").doesNotExist());

        mvc.perform(get("/orchestrator/workflows/WF-000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("TERMINE"))
                .andExpect(jsonPath("$.etapes.length()").value(6))
                .andExpect(jsonPath("$.etapes[*].nom").value(org.hamcrest.Matchers.contains("ALERTE_RECUE", "DROITS_CNAM",
                        "CRENEAU_URGENCE", "ALERTE_TABLEAU_DE_BORD", "COUVERTURE", "FICHE_URGENCE")))
                .andExpect(jsonPath("$.etapes[1].service").value("SOAP"))
                .andExpect(jsonPath("$.etapes[1].tentatives").value(2))
                .andExpect(jsonPath("$.etapes[1].statut").value("OK"))
                .andExpect(jsonPath("$.etapes[2].detail").value("RDV-000001 à 17:30"));
    }

    @Test
    void echecEtAmbulanceFiltresParEtatEtPatient() throws Exception {
        rdv = Optional.empty();
        workflow().traiter(ALERTE);                                                   // WF-000001 AMBULANCE
        pannesSoap = 99;
        workflow().traiter(new AlerteRecue("ALR-000002", "TN-0089012", "URGENT", "SPO2", 88, 90, "SpO2 88 % < 90 %",
                OffsetDateTime.parse("2026-11-22T17:25:00+01:00")));                  // WF-000002 ECHEC

        mvc.perform(get("/orchestrator/workflows"))
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.contains("WF-000002", "WF-000001")));
        mvc.perform(get("/orchestrator/workflows").param("etat", "AMBULANCE"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].interventionId").value("AMB-000001"))
                .andExpect(jsonPath("$[0].rendezVousId").doesNotExist());
        mvc.perform(get("/orchestrator/workflows").param("patientId", "TN-0089012"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].etat").value("ECHEC"))
                .andExpect(jsonPath("$[0].erreur").value("DROITS_CNAM (SOAP) après 3 essais : SOAP indisponible : pas de réponse"));
        mvc.perform(get("/orchestrator/workflows/WF-000002"))
                .andExpect(jsonPath("$.etapes.length()").value(2))
                .andExpect(jsonPath("$.etapes[1].statut").value("ECHEC"))
                .andExpect(jsonPath("$.etapes[1].tentatives").value(3));
        mvc.perform(get("/orchestrator/workflows").param("limite", "1"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void erreursAuFormatProblemDetails() throws Exception {
        mvc.perform(get("/orchestrator/workflows/WF-999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problemes/non-trouve"));
        mvc.perform(get("/orchestrator/workflows/inconnu")).andExpect(status().isNotFound());
        for (String[] p : new String[][] {{"limite", "0"}, {"limite", "201"}, {"limite", "abc"}, {"etat", "FINI"}, {"patientId", "TN-12"}}) {
            mvc.perform(get("/orchestrator/workflows").param(p[0], p[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("/problemes/requete-invalide"));
        }
    }
}
