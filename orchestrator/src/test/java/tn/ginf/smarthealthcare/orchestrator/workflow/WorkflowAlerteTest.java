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
import org.junit.jupiter.api.Test;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.AlerteRecue;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.Droits;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.InterventionAmbulance;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.PriseEnCharge;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.RendezVousUrgence;
import tn.ginf.smarthealthcare.orchestrator.workflow.WorkflowAlerte.Etat;

/** Enchaînement du workflow avec de faux services (sans réseau). */
class WorkflowAlerteTest {

    static final AlerteRecue ALERTE = new AlerteRecue("ALR-000001", "TN-0012345", "CRITIQUE", "FREQUENCE_CARDIAQUE",
            165, 140, "Fréquence cardiaque 165 bpm > 140 bpm", OffsetDateTime.parse("2026-11-17T23:30:00Z"));

    final List<String> appels = new ArrayList<>();
    LocalDate dateDemandee;
    Droits droits = new Droits("OUVERTS", true, 80, null, "VIG-2026-100001", LocalDate.of(2026, 12, 31));
    Optional<RendezVousUrgence> rdv = Optional.of(new RendezVousUrgence("RDV-000001", OffsetDateTime.parse("2026-11-18T08:00:00+01:00")));
    /** Ambulance renvoyée par le faux service REST (P5.2) : arrivée 20 min après le déclenchement. */
    InterventionAmbulance ambulance = new InterventionAmbulance("AMB-000001", OffsetDateTime.parse("2026-11-18T00:50:00+01:00"));
    boolean ficheARegulariser;
    String commentaireFiche;
    RendezVousUrgence rdvFiche;
    InterventionAmbulance ambulanceFiche;
    final List<PriseEnCharge> resultatsDiffuses = new ArrayList<>();
    /** Fin du workflow : 17h20m01,5 UTC → 18h20m01 à Tunis (tronqué à la seconde). */
    static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-22T17:20:01.500Z"), ZoneOffset.UTC);

    final Ports.Diffusion diffusion = new Ports.Diffusion() {
        public void alerte(AlerteRecue a) { appels.add("SSE alerte " + a.id()); }
        public void priseEnCharge(PriseEnCharge p) { appels.add("SSE prise-en-charge " + p.etat()); resultatsDiffuses.add(p); }
    };

    final Ports.Portail portail = new Ports.Portail() {
        public Optional<RendezVousUrgence> reserverUrgence(AlerteRecue a, boolean aReg) {
            appels.add("REST urgence " + a.id() + " aRegulariser=" + aReg);
            return rdv;
        }

        public InterventionAmbulance envoyerAmbulance(AlerteRecue a, boolean aReg) {
            appels.add("REST ambulance " + a.id() + " aRegulariser=" + aReg);
            return ambulance;
        }
    };

    final Ports.TableauDeBord tableauDeBord = new Ports.TableauDeBord() {
        public void enregistrerAlerte(AlerteRecue a) { appels.add("GraphQL alerte " + a.id()); }
        public void mettreAJourCouverture(String p, Droits d) { appels.add("GraphQL couverture " + p + " " + d.statut()); }
        public void enregistrerConstantes(String p, List<Ports.ConstanteMesuree> c) { appels.add("GraphQL constantes " + p); }
        public String creerFicheUrgence(AlerteRecue a, boolean aReg, String commentaire, RendezVousUrgence r,
                                        InterventionAmbulance m) {
            appels.add("GraphQL fiche " + (r != null ? r.id() : m.id()));
            ficheARegulariser = aReg;
            commentaireFiche = commentaire;
            rdvFiche = r;
            ambulanceFiche = m;
            return "FIC-000001";
        }
    };

    WorkflowAlerte workflow() {
        Ports.Dossier dossier = (patient, date) -> { appels.add("SOAP " + patient); dateDemandee = date; return droits; };
        return new WorkflowAlerte(dossier, portail, tableauDeBord, diffusion, "Africa/Tunis", HORLOGE);
    }

    @Test
    void enchaineSoapPuisRestPuisGraphqlDansLOrdre() {
        var r = workflow().traiter(ALERTE);
        assertThat(r.etat()).isEqualTo(Etat.TERMINE);
        assertThat(r.ficheId()).isEqualTo("FIC-000001");
        assertThat(appels).containsExactly("SSE alerte ALR-000001", "SOAP TN-0012345", "REST urgence ALR-000001 aRegulariser=false",
                "GraphQL alerte ALR-000001", "GraphQL couverture TN-0012345 OUVERTS", "GraphQL fiche RDV-000001",
                "SSE prise-en-charge TERMINE");
        assertThat(resultatsDiffuses).containsExactly(new PriseEnCharge("ALR-000001", "TN-0012345", "TERMINE", false,
                "RDV-000001", OffsetDateTime.parse("2026-11-18T08:00:00+01:00"), "FIC-000001", null, null,
                OffsetDateTime.parse("2026-11-22T18:20:01+01:00")));
        assertThat(ambulanceFiche).isNull();
        // 23h30 UTC = 00h30 le lendemain à Tunis : la date de prestation suit le fuseau local
        assertThat(dateDemandee).isEqualTo(LocalDate.of(2026, 11, 18));
    }

    @Test
    void patientCouvertSansCommentaire() {
        workflow().traiter(ALERTE);
        assertThat(ficheARegulariser).isFalse();
        assertThat(commentaireFiche).isNull();
    }

    @Test
    void droitsExpiresPriseEnChargeARegulariserAvecLaRaison() {
        // TN-0067890 du seed : vignette 2025 expirée (réponse SOAP réelle)
        droits = new Droits("EXPIRES", false, 80, "Vignette valide du 2025-01-01 au 2025-12-31",
                "VIG-2025-100006", LocalDate.of(2025, 12, 31));
        var r = workflow().traiter(ALERTE);
        // les soins ne sont pas bloqués : rendez-vous et fiche sont créés
        assertThat(r.etat()).isEqualTo(Etat.TERMINE);
        assertThat(r.rendezVousId()).isEqualTo("RDV-000001");
        assertThat(r.aRegulariser()).isTrue();
        assertThat(appels).containsExactly("SSE alerte ALR-000001", "SOAP TN-0012345", "REST urgence ALR-000001 aRegulariser=true",
                "GraphQL alerte ALR-000001", "GraphQL couverture TN-0012345 EXPIRES", "GraphQL fiche RDV-000001",
                "SSE prise-en-charge TERMINE");
        assertThat(ficheARegulariser).isTrue();
        assertThat(commentaireFiche).isEqualTo("À régulariser : droits CNAM expirés (vignette VIG-2025-100006)"
                + " — Vignette valide du 2025-01-01 au 2025-12-31. Soins assurés, dossier administratif à compléter.");
        assertThat(resultatsDiffuses).singleElement().satisfies(p -> assertThat(p.aRegulariser()).isTrue());
    }

    @Test
    void patientNonAffiliePriseEnChargeARegulariser() {
        // TN-0089012 du seed : aucune couverture (réponse SOAP réelle)
        droits = new Droits("NON_AFFILIE", false, null, "Aucune affiliation CNAM pour ce patient", null, null);
        var r = workflow().traiter(ALERTE);
        assertThat(r.etat()).isEqualTo(Etat.TERMINE);
        assertThat(r.aRegulariser()).isTrue();
        assertThat(appels).contains("REST urgence ALR-000001 aRegulariser=true", "GraphQL couverture TN-0012345 NON_AFFILIE");
        assertThat(commentaireFiche).isEqualTo("À régulariser : patient non affilié à la CNAM"
                + " — Aucune affiliation CNAM pour ce patient. Soins assurés, dossier administratif à compléter.");
    }

    @Test
    void aucunCreneauLibreEnvoieUneAmbulanceEtCreeLaFiche() {
        rdv = Optional.empty();
        var r = workflow().traiter(ALERTE);
        assertThat(r.etat()).isEqualTo(Etat.AMBULANCE);
        assertThat(r.ficheId()).isEqualTo("FIC-000001");
        assertThat(r.interventionId()).isEqualTo("AMB-000001");
        assertThat(r.rendezVousId()).isNull();
        // l'ambulance est demandée juste après le refus du créneau, puis la fiche est consolidée
        assertThat(appels).containsExactly("SSE alerte ALR-000001", "SOAP TN-0012345",
                "REST urgence ALR-000001 aRegulariser=false", "REST ambulance ALR-000001 aRegulariser=false",
                "GraphQL alerte ALR-000001", "GraphQL couverture TN-0012345 OUVERTS", "GraphQL fiche AMB-000001",
                "SSE prise-en-charge AMBULANCE");
        assertThat(rdvFiche).isNull();
        assertThat(ambulanceFiche).isEqualTo(ambulance);
        assertThat(commentaireFiche).isEqualTo(
                "Aucun créneau d'urgence libre : ambulance AMB-000001 envoyée au domicile, arrivée estimée à 00:50.");
        assertThat(resultatsDiffuses).containsExactly(new PriseEnCharge("ALR-000001", "TN-0012345", "AMBULANCE", false,
                null, null, "FIC-000001", "AMB-000001", OffsetDateTime.parse("2026-11-18T00:50:00+01:00"),
                OffsetDateTime.parse("2026-11-22T18:20:01+01:00")));
    }

    @Test
    void ambulancePourUnPatientNonCouvertGardeLaRegularisation() {
        droits = new Droits("NON_AFFILIE", false, null, "Aucune affiliation CNAM pour ce patient", null, null);
        rdv = Optional.empty();
        var r = workflow().traiter(ALERTE);
        assertThat(r.etat()).isEqualTo(Etat.AMBULANCE);
        assertThat(appels).contains("REST ambulance ALR-000001 aRegulariser=true");
        assertThat(ficheARegulariser).isTrue();
        assertThat(commentaireFiche).isEqualTo(
                "Aucun créneau d'urgence libre : ambulance AMB-000001 envoyée au domicile, arrivée estimée à 00:50."
                        + " À régulariser : patient non affilié à la CNAM — Aucune affiliation CNAM pour ce patient."
                        + " Soins assurés, dossier administratif à compléter.");
        assertThat(resultatsDiffuses).singleElement().satisfies(p -> assertThat(p.aRegulariser()).isTrue());
    }

    @Test
    void ambulanceImpossibleDonneEchec() {
        rdv = Optional.empty();
        Ports.Portail sansAmbulance = new Ports.Portail() {
            public Optional<RendezVousUrgence> reserverUrgence(AlerteRecue a, boolean x) { return Optional.empty(); }
            public InterventionAmbulance envoyerAmbulance(AlerteRecue a, boolean x) { throw new IllegalStateException("REST 503"); }
        };
        var w = new WorkflowAlerte((p, d) -> droits, sansAmbulance, tableauDeBord, diffusion, "Africa/Tunis", HORLOGE);
        assertThat(w.traiter(ALERTE).etat()).isEqualTo(Etat.ECHEC);
        assertThat(appels).noneMatch(a -> a.startsWith("GraphQL"));
        assertThat(resultatsDiffuses).singleElement().satisfies(p -> {
            assertThat(p.etat()).isEqualTo("ECHEC");
            assertThat(p.ficheId()).isNull();
            assertThat(p.interventionId()).isNull();
        });
    }

    @Test
    void erreurDUnServiceDonneEchecSansExceptionPropagee() {
        Ports.Dossier enPanne = (p, d) -> { throw new IllegalStateException("SOAP PATIENT_INCONNU"); };
        var w = new WorkflowAlerte(enPanne, portail, null, diffusion, "Africa/Tunis", HORLOGE);
        assertThat(w.traiter(ALERTE).etat()).isEqualTo(Etat.ECHEC);
        // l'alerte a été diffusée avant la panne, puis l'échec
        assertThat(appels).containsExactly("SSE alerte ALR-000001", "SSE prise-en-charge ECHEC");
    }

    @Test
    void panneDeLaDiffusionNInterromptPasLaPriseEnCharge() {
        Ports.Diffusion enPanne = new Ports.Diffusion() {
            public void alerte(AlerteRecue a) { throw new IllegalStateException("aucun client"); }
            public void priseEnCharge(PriseEnCharge p) { throw new IllegalStateException("aucun client"); }
        };
        var w = new WorkflowAlerte((p, d) -> droits, portail, tableauDeBord, enPanne, "Africa/Tunis", HORLOGE);
        var r = w.traiter(ALERTE);
        assertThat(r.etat()).isEqualTo(Etat.TERMINE);
        assertThat(r.rendezVousId()).isEqualTo("RDV-000001");
    }
}
