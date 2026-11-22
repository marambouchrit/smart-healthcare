package tn.ginf.smarthealthcare.orchestrator.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Politique de nouveaux essais « sûrs » (P5.3, D54). */
class TentativesTest {

    final List<Duration> attentes = new ArrayList<>();
    final Tentatives tentatives = new Tentatives(3, Duration.ofMillis(500), attentes::add);
    final AtomicInteger appels = new AtomicInteger();

    static ServiceIndisponibleException panne(boolean envoyee) {
        return new ServiceIndisponibleException("REST", envoyee, "test", null);
    }

    @Test
    void reussiteDuPremierCoupSansAttente() {
        var issue = tentatives.executer(false, () -> { appels.incrementAndGet(); return "ok"; });
        assertThat(issue.reussie()).isTrue();
        assertThat(issue.valeur()).isEqualTo("ok");
        assertThat(issue.essais()).isEqualTo(1);
        assertThat(attentes).isEmpty();
    }

    @Test
    void lectureReessayeeAvecAttenteDoubleePuisReussie() {
        var issue = tentatives.executer(false, () -> {
            if (appels.incrementAndGet() < 3) throw panne(true);   // pas de réponse : sûr pour une lecture
            return "ok";
        });
        assertThat(issue.valeur()).isEqualTo("ok");
        assertThat(issue.essais()).isEqualTo(3);
        assertThat(attentes).containsExactly(Duration.ofMillis(500), Duration.ofMillis(1000));
    }

    @Test
    void auPlusTroisEssaisPuisLaDernierePanne() {
        var issue = tentatives.executer(false, () -> { appels.incrementAndGet(); throw panne(true); });
        assertThat(issue.reussie()).isFalse();
        assertThat(issue.erreur()).isInstanceOf(ServiceIndisponibleException.class);
        assertThat(issue.essais()).isEqualTo(3);
        assertThat(appels).hasValue(3);
    }

    @Test
    void creationReessayeeSeulementSiLaRequeteNaPasPuPartir() {
        var nonPartie = tentatives.executer(true, () -> {
            if (appels.incrementAndGet() < 2) throw panne(false);  // connexion refusée : rien n'a été créé
            return "RDV-000001";
        });
        assertThat(nonPartie.valeur()).isEqualTo("RDV-000001");
        assertThat(nonPartie.essais()).isEqualTo(2);

        appels.set(0);
        var partie = tentatives.executer(true, () -> { appels.incrementAndGet(); throw panne(true); });
        // la requête est peut-être arrivée : pas de nouvel essai, pour ne pas créer de doublon
        assertThat(partie.reussie()).isFalse();
        assertThat(partie.essais()).isEqualTo(1);
        assertThat(appels).hasValue(1);
    }

    @Test
    void erreurMetierJamaisReessayee() {
        var issue = tentatives.executer(false, () -> {
            appels.incrementAndGet();
            throw new IllegalStateException("SOAP PATIENT_INCONNU : Aucun dossier");
        });
        assertThat(issue.reussie()).isFalse();
        assertThat(issue.essais()).isEqualTo(1);
        assertThat(issue.erreur()).hasMessageContaining("PATIENT_INCONNU");
        assertThat(attentes).isEmpty();
    }
}
