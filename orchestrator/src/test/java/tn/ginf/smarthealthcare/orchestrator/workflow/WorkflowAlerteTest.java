package tn.ginf.smarthealthcare.orchestrator.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.AlerteRecue;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.Droits;
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

    WorkflowAlerte workflow() {
        Ports.Dossier dossier = (patient, date) -> { appels.add("SOAP " + patient); dateDemandee = date; return droits; };
        Ports.Portail portail = (a, aReg) -> { appels.add("REST " + a.id() + " aRegulariser=" + aReg); return rdv; };
        Ports.TableauDeBord tdb = new Ports.TableauDeBord() {
            public void enregistrerAlerte(AlerteRecue a) { appels.add("GraphQL alerte " + a.id()); }
            public void mettreAJourCouverture(String p, Droits d) { appels.add("GraphQL couverture " + p + " " + d.statut()); }
            public String creerFicheUrgence(AlerteRecue a, boolean aReg, RendezVousUrgence r) {
                appels.add("GraphQL fiche " + r.id()); ficheARegulariser = aReg; return "FIC-000001";
            }
        };
        return new WorkflowAlerte(dossier, portail, tdb, "Africa/Tunis");
    }

    @Test
    void enchaineSoapPuisRestPuisGraphqlDansLOrdre() {
        var r = workflow().traiter(ALERTE);
        assertThat(r.etat()).isEqualTo(Etat.TERMINE);
        assertThat(r.ficheId()).isEqualTo("FIC-000001");
        assertThat(appels).containsExactly("SOAP TN-0012345", "REST ALR-000001 aRegulariser=false",
                "GraphQL alerte ALR-000001", "GraphQL couverture TN-0012345 OUVERTS", "GraphQL fiche RDV-000001");
        // 23h30 UTC = 00h30 le lendemain à Tunis : la date de prestation suit le fuseau local
        assertThat(dateDemandee).isEqualTo(LocalDate.of(2026, 11, 18));
    }

    @Test
    void patientNonCouvertFicheARegulariser() {
        droits = new Droits("EXPIRES", false, 80, "Vignette expirée", "VIG-2025-100006", LocalDate.of(2025, 12, 31));
        var r = workflow().traiter(ALERTE);
        assertThat(r.etat()).isEqualTo(Etat.TERMINE);
        assertThat(r.aRegulariser()).isTrue();
        assertThat(ficheARegulariser).isTrue();
        assertThat(appels).contains("REST ALR-000001 aRegulariser=true");
    }

    @Test
    void aucunCreneauArreteAvantGraphql() {
        rdv = Optional.empty();
        var r = workflow().traiter(ALERTE);
        assertThat(r.etat()).isEqualTo(Etat.AUCUN_CRENEAU);
        assertThat(appels).noneMatch(a -> a.startsWith("GraphQL"));
    }

    @Test
    void erreurDUnServiceDonneEchecSansExceptionPropagee() {
        Ports.Dossier enPanne = (p, d) -> { throw new IllegalStateException("SOAP PATIENT_INCONNU"); };
        var w = new WorkflowAlerte(enPanne, (a, x) -> rdv, null, "Africa/Tunis");
        assertThat(w.traiter(ALERTE).etat()).isEqualTo(Etat.ECHEC);
    }
}
