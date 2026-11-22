package tn.ginf.smarthealthcare.orchestrator.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.AlerteRecue;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.ConstanteMesuree;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.Droits;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.RendezVousUrgence;

/** P2.GRPC.5 : regroupement et envoi périodique des constantes (sans réseau). */
class RelaisConstantesTest {

    static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-17T10:00:00+01:00");

    final List<String> envois = new ArrayList<>();
    boolean enPanne;

    final Ports.TableauDeBord tableauDeBord = new Ports.TableauDeBord() {
        public void enregistrerAlerte(AlerteRecue a) {}
        public void mettreAJourCouverture(String p, Droits d) {}
        public String creerFicheUrgence(AlerteRecue a, boolean r, String c, RendezVousUrgence v) { return null; }
        public void enregistrerConstantes(String p, List<ConstanteMesuree> c) {
            if (enPanne) {
                throw new IllegalStateException("GraphQL indisponible");
            }
            envois.add(p + " " + c.stream().map(x -> x.type() + "=" + (int) x.valeur()).toList());
        }
    };

    @Test
    void laDerniereValeurDeChaqueTypeEstEnvoyeeUneFoisParPatient() {
        RelaisConstantes r = new RelaisConstantes(tableauDeBord);
        r.recevoir("TN-0012345", "FREQUENCE_CARDIAQUE", 80, T0);
        r.recevoir("TN-0012345", "FREQUENCE_CARDIAQUE", 84, T0.plusSeconds(1));
        r.recevoir("TN-0012345", "SPO2", 96, T0);
        r.recevoir("TN-0034567", "SPO2", 93, T0);
        r.recevoir("TN-0012345", "FREQUENCE_CARDIAQUE", 70, T0.minusSeconds(5)); // en retard : ignorée

        assertThat(r.transmettre()).isEqualTo(2);
        assertThat(envois).containsExactly("TN-0012345 [FREQUENCE_CARDIAQUE=84, SPO2=96]", "TN-0034567 [SPO2=93]");
        assertThat(r.transmettre()).isZero(); // rien de nouveau : aucun appel
        assertThat(envois).hasSize(2);
    }

    @Test
    void enCasDEchecLesValeursSontRenvoyeesAuProchainEnvoi() {
        RelaisConstantes r = new RelaisConstantes(tableauDeBord);
        r.recevoir("TN-0012345", "SPO2", 95, T0);
        enPanne = true;
        assertThat(r.transmettre()).isZero();
        assertThat(r.nombreEnAttente()).isEqualTo(1);
        enPanne = false;
        assertThat(r.transmettre()).isEqualTo(1);
        assertThat(envois).containsExactly("TN-0012345 [SPO2=95]");
    }
}
