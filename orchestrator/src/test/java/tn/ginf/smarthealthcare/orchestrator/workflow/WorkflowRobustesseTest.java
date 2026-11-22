package tn.ginf.smarthealthcare.orchestrator.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.AlerteRecue;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.Droits;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.EtapeTrace;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.InterventionAmbulance;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.PriseEnCharge;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.RendezVousUrgence;
import tn.ginf.smarthealthcare.orchestrator.workflow.WorkflowAlerte.Etat;

/** Délais, nouveaux essais et trace de chaque instance (P5.3, D54), avec de faux services. */
class WorkflowRobustesseTest {

    static final AlerteRecue ALERTE = WorkflowAlerteTest.ALERTE;
    static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-22T17:20:01Z"), ZoneOffset.UTC);
    static final Droits OUVERTS = new Droits("OUVERTS", true, 80, null, "VIG-2026-100001", LocalDate.of(2026, 12, 31));
    static final RendezVousUrgence RDV = new RendezVousUrgence("RDV-000001", OffsetDateTime.parse("2026-11-18T08:00:00+01:00"));

    // ---------- fausse trace ----------
    final List<EtapeTrace> etapes = new ArrayList<>();
    final List<String> fins = new ArrayList<>();
    final Ports.Traces traces = new Ports.Traces() {
        public long demarrer(AlerteRecue a, OffsetDateTime debut) { fins.add("début " + a.id() + " " + debut); return 7; }
        public void etape(long id, EtapeTrace e) { assertThat(id).isEqualTo(7); etapes.add(e); }
        public void terminer(long id, PriseEnCharge r, String erreur, OffsetDateTime fin) { fins.add("fin " + r.etat() + (erreur == null ? "" : " : " + erreur)); }
    };

    // ---------- faux services réglables ----------
    final AtomicInteger appelsSoap = new AtomicInteger();
    final AtomicInteger appelsUrgence = new AtomicInteger();
    final AtomicInteger appelsFiche = new AtomicInteger();
    int pannesSoap;
    int pannesUrgence;
    boolean urgencePartie = true;
    Optional<RendezVousUrgence> rdv = Optional.of(RDV);
    final List<String> diffuses = new ArrayList<>();

    final Ports.Dossier dossier = (p, d) -> {
        if (appelsSoap.incrementAndGet() <= pannesSoap) throw new ServiceIndisponibleException("SOAP", true, "pas de réponse", null);
        return OUVERTS;
    };
    final Ports.Portail portail = new Ports.Portail() {
        public Optional<RendezVousUrgence> reserverUrgence(AlerteRecue a, boolean r) {
            if (appelsUrgence.incrementAndGet() <= pannesUrgence) {
                throw new ServiceIndisponibleException("REST", urgencePartie, urgencePartie ? "pas de réponse" : "connexion impossible", null);
            }
            return rdv;
        }
        public InterventionAmbulance envoyerAmbulance(AlerteRecue a, boolean r) {
            return new InterventionAmbulance("AMB-000001", OffsetDateTime.parse("2026-11-18T00:50:00+01:00"));
        }
    };
    final Ports.TableauDeBord tableauDeBord = new Ports.TableauDeBord() {
        public void enregistrerAlerte(AlerteRecue a) {}
        public void mettreAJourCouverture(String p, Droits d) {}
        public void enregistrerConstantes(String p, List<Ports.ConstanteMesuree> c) {}
        public String creerFicheUrgence(AlerteRecue a, boolean r, String c, RendezVousUrgence v, InterventionAmbulance m) {
            appelsFiche.incrementAndGet();
            return "FIC-000001";
        }
    };
    final Ports.Diffusion diffusion = new Ports.Diffusion() {
        public void alerte(AlerteRecue a) { diffuses.add("alerte"); }
        public void priseEnCharge(PriseEnCharge p) { diffuses.add(p.etat()); }
    };

    WorkflowAlerte workflow(Ports.Traces t) {
        return new WorkflowAlerte(dossier, portail, tableauDeBord, diffusion, t, Tentatives.sansAttente(3), "Africa/Tunis", HORLOGE);
    }

    static String resume(EtapeTrace e) {
        return e.ordre() + " " + e.nom() + " " + e.service() + " x" + e.tentatives() + " " + e.statut() + " " + e.detail();
    }

    @Test
    void chaqueEtapeEstTraceeDansLOrdre() {
        var r = workflow(traces).traiter(ALERTE);
        assertThat(r.etat()).isEqualTo(Etat.TERMINE);
        assertThat(etapes).extracting(WorkflowRobustesseTest::resume).containsExactly(
                "1 ALERTE_RECUE gRPC x1 OK CRITIQUE — Fréquence cardiaque 165 bpm > 140 bpm",
                "2 DROITS_CNAM SOAP x1 OK OUVERTS 80 %",
                "3 CRENEAU_URGENCE REST x1 OK RDV-000001 à 08:00",
                "4 ALERTE_TABLEAU_DE_BORD GraphQL x1 OK ALR-000001",
                "5 COUVERTURE GraphQL x1 OK OUVERTS",
                "6 FICHE_URGENCE GraphQL x1 OK FIC-000001");
        // instant précis dans la trace (même instant que 18:20:01 à Tunis) ; l'API l'affiche à Tunis, à la seconde
        assertThat(etapes.get(0).debut().toInstant()).isEqualTo(Instant.parse("2026-11-22T17:20:01Z"));
        assertThat(fins).containsExactly("début ALR-000001 2026-11-22T17:20:01Z", "fin TERMINE");
    }

    @Test
    void aucunCreneauTraceUnRefusPuisLAmbulance() {
        rdv = Optional.empty();
        var r = workflow(traces).traiter(ALERTE);
        assertThat(r.etat()).isEqualTo(Etat.AMBULANCE);
        assertThat(etapes).extracting(WorkflowRobustesseTest::resume).contains(
                "3 CRENEAU_URGENCE REST x1 REFUS aucun créneau d'urgence libre (409)",
                "4 AMBULANCE REST x1 OK AMB-000001, arrivée estimée à 00:50");
        assertThat(fins).last().isEqualTo("fin AMBULANCE");
    }

    @Test
    void soapSansReponseEstReessayeEtLeWorkflowAboutit() {
        pannesSoap = 2;   // lecture : réessayée même si la requête est partie
        var r = workflow(traces).traiter(ALERTE);
        assertThat(r.etat()).isEqualTo(Etat.TERMINE);
        assertThat(appelsSoap).hasValue(3);
        assertThat(etapes.get(1)).extracting(WorkflowRobustesseTest::resume).isEqualTo("2 DROITS_CNAM SOAP x3 OK OUVERTS 80 %");
    }

    @Test
    void soapIndisponibleApresTroisEssaisDonneEchecTrace() {
        pannesSoap = 99;
        var r = workflow(traces).traiter(ALERTE);
        assertThat(r.etat()).isEqualTo(Etat.ECHEC);
        assertThat(appelsSoap).hasValue(3);
        assertThat(appelsUrgence).hasValue(0);
        assertThat(etapes).extracting(WorkflowRobustesseTest::resume).containsExactly(
                "1 ALERTE_RECUE gRPC x1 OK CRITIQUE — Fréquence cardiaque 165 bpm > 140 bpm",
                "2 DROITS_CNAM SOAP x3 ECHEC SOAP indisponible : pas de réponse");
        assertThat(fins).last().isEqualTo("fin ECHEC : DROITS_CNAM (SOAP) après 3 essais : SOAP indisponible : pas de réponse");
        assertThat(diffuses).containsExactly("alerte", "ECHEC");
    }

    @Test
    void urgenceSansReponseNestPasRejoueePourEviterUnDoublon() {
        pannesUrgence = 1;
        urgencePartie = true;    // la réservation est peut-être faite : ne pas recommencer
        var r = workflow(traces).traiter(ALERTE);
        assertThat(r.etat()).isEqualTo(Etat.ECHEC);
        assertThat(appelsUrgence).hasValue(1);
        assertThat(appelsFiche).hasValue(0);
        assertThat(etapes.get(2)).extracting(WorkflowRobustesseTest::resume)
                .isEqualTo("3 CRENEAU_URGENCE REST x1 ECHEC REST indisponible : pas de réponse");
    }

    @Test
    void urgenceRefuseeALaConnexionEstReessayee() {
        pannesUrgence = 2;
        urgencePartie = false;   // connexion refusée : rien n'a été réservé, on peut réessayer
        var r = workflow(traces).traiter(ALERTE);
        assertThat(r.etat()).isEqualTo(Etat.TERMINE);
        assertThat(appelsUrgence).hasValue(3);
        assertThat(etapes.get(2)).extracting(WorkflowRobustesseTest::resume).isEqualTo("3 CRENEAU_URGENCE REST x3 OK RDV-000001 à 08:00");
    }

    @Test
    void uneTraceEnPanneNInterromptPasLaPriseEnCharge() {
        Ports.Traces enPanne = new Ports.Traces() {
            public long demarrer(AlerteRecue a, OffsetDateTime d) { throw new IllegalStateException("base indisponible"); }
            public void etape(long id, EtapeTrace e) { throw new IllegalStateException("base indisponible"); }
            public void terminer(long id, PriseEnCharge r, String e, OffsetDateTime f) { throw new IllegalStateException("base indisponible"); }
        };
        var r = workflow(enPanne).traiter(ALERTE);
        assertThat(r.etat()).isEqualTo(Etat.TERMINE);
        assertThat(diffuses).containsExactly("alerte", "TERMINE");
    }
}
