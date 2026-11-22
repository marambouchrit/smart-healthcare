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
    boolean ficheARegulariser;
    String commentaireFiche;
    final List<PriseEnCharge> resultatsDiffuses = new ArrayList<>();
    /** Fin du workflow : 17h20m01,5 UTC → 18h20m01 à Tunis (tronqué à la seconde). */
    static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-22T17:20:01.500Z"), ZoneOffset.UTC);

    final Ports.Diffusion diffusion = new Ports.Diffusion() {
        public void alerte(AlerteRecue a) { appels.add("SSE alerte " + a.id()); }
        public void priseEnCharge(PriseEnCharge p) { appels.add("SSE prise-en-charge " + p.etat()); resultatsDiffuses.add(p); }
    };

    WorkflowAlerte workflow() {
        Ports.Dossier dossier = (patient, date) -> { appels.add("SOAP " + patient); dateDemandee = date; return droits; };
        Ports.Portail portail = (a, aReg) -> { appels.add("REST " + a.id() + " aRegulariser=" + aReg); return rdv; };
        Ports.TableauDeBord tdb = new Ports.TableauDeBord() {
            public void enregistrerAlerte(AlerteRecue a) { appels.add("GraphQL alerte " + a.id()); }
            public void mettreAJourCouverture(String p, Droits d) { appels.add("GraphQL couverture " + p + " " + d.statut()); }
            public void enregistrerConstantes(String p, List<Ports.ConstanteMesuree> c) { appels.add("GraphQL constantes " + p); }
            public String creerFicheUrgence(AlerteRecue a, boolean aReg, String commentaire, RendezVousUrgence r) {
                appels.add("GraphQL fiche " + r.id()); ficheARegulariser = aReg; commentaireFiche = commentaire; return "FIC-000001";
            }
        };
        return new WorkflowAlerte(dossier, portail, tdb, diffusion, "Africa/Tunis", HORLOGE);
    }

    @Test
    void enchaineSoapPuisRestPuisGraphqlDansLOrdre() {
        var r = workflow().traiter(ALERTE);
        assertThat(r.etat()).isEqualTo(Etat.TERMINE);
        assertThat(r.ficheId()).isEqualTo("FIC-000001");
        assertThat(appels).containsExactly("SSE alerte ALR-000001", "SOAP TN-0012345", "REST ALR-000001 aRegulariser=false",
                "GraphQL alerte ALR-000001", "GraphQL couverture TN-0012345 OUVERTS", "GraphQL fiche RDV-000001",
                "SSE prise-en-charge TERMINE");
        assertThat(resultatsDiffuses).containsExactly(new PriseEnCharge("ALR-000001", "TN-0012345", "TERMINE", false,
                "RDV-000001", OffsetDateTime.parse("2026-11-18T08:00:00+01:00"), "FIC-000001",
                OffsetDateTime.parse("2026-11-22T18:20:01+01:00")));
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
        assertThat(appels).containsExactly("SSE alerte ALR-000001", "SOAP TN-0012345", "REST ALR-000001 aRegulariser=true",
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
        assertThat(appels).contains("REST ALR-000001 aRegulariser=true", "GraphQL couverture TN-0012345 NON_AFFILIE");
        assertThat(commentaireFiche).isEqualTo("À régulariser : patient non affilié à la CNAM"
                + " — Aucune affiliation CNAM pour ce patient. Soins assurés, dossier administratif à compléter.");
    }

    @Test
    void aucunCreneauPourUnPatientNonCouvertGardeLIndicateur() {
        droits = new Droits("NON_AFFILIE", false, null, "Aucune affiliation CNAM pour ce patient", null, null);
        rdv = Optional.empty();
        var r = workflow().traiter(ALERTE);
        assertThat(r.etat()).isEqualTo(Etat.AUCUN_CRENEAU);
        assertThat(resultatsDiffuses).singleElement().satisfies(p -> assertThat(p.aRegulariser()).isTrue());
    }

    @Test
    void aucunCreneauArreteAvantGraphql() {
        rdv = Optional.empty();
        var r = workflow().traiter(ALERTE);
        assertThat(r.etat()).isEqualTo(Etat.AUCUN_CRENEAU);
        assertThat(appels).noneMatch(a -> a.startsWith("GraphQL"));
        assertThat(resultatsDiffuses).singleElement().satisfies(p -> {
            assertThat(p.etat()).isEqualTo("AUCUN_CRENEAU");
            assertThat(p.rendezVousId()).isNull();
            assertThat(p.debutRendezVous()).isNull();
            assertThat(p.ficheId()).isNull();
        });
    }

    @Test
    void erreurDUnServiceDonneEchecSansExceptionPropagee() {
        Ports.Dossier enPanne = (p, d) -> { throw new IllegalStateException("SOAP PATIENT_INCONNU"); };
        var w = new WorkflowAlerte(enPanne, (a, x) -> rdv, null, diffusion, "Africa/Tunis", HORLOGE);
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
        var w = new WorkflowAlerte((p, d) -> droits, (a, x) -> rdv, new Ports.TableauDeBord() {
            public void enregistrerAlerte(AlerteRecue a) {}
            public void mettreAJourCouverture(String p, Droits d) {}
            public void enregistrerConstantes(String p, List<Ports.ConstanteMesuree> c) {}
            public String creerFicheUrgence(AlerteRecue a, boolean aReg, String c, RendezVousUrgence r) { return "FIC-000001"; }
        }, enPanne, "Africa/Tunis", HORLOGE);
        var r = w.traiter(ALERTE);
        assertThat(r.etat()).isEqualTo(Etat.TERMINE);
        assertThat(r.rendezVousId()).isEqualTo("RDV-000001");
    }
}
