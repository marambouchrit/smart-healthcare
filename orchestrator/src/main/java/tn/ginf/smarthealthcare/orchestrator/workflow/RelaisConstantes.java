package tn.ginf.smarthealthcare.orchestrator.workflow;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.ConstanteMesuree;

/**
 * Relais des mesures vers le tableau de bord (P2.GRPC.5, D18, D39) : les mesures reçues par SuivreMesures sont
 * regroupées (dernière valeur de chaque type, par patient) et transmises périodiquement par enregistrerConstantes.
 * En cas d'échec, les valeurs non transmises sont conservées pour l'envoi suivant (sauf si une plus récente arrive).
 */
@Component
public class RelaisConstantes {

    private static final Logger log = LoggerFactory.getLogger(RelaisConstantes.class);

    private final Ports.TableauDeBord tableauDeBord;
    /** patient → (type → dernière mesure non encore transmise) */
    private final Map<String, Map<String, ConstanteMesuree>> enAttente = new LinkedHashMap<>();

    public RelaisConstantes(Ports.TableauDeBord tableauDeBord) {
        this.tableauDeBord = tableauDeBord;
    }

    public synchronized void recevoir(String patientId, String type, double valeur, OffsetDateTime horodatage) {
        garderLaPlusRecente(patientId, new ConstanteMesuree(type, valeur, horodatage));
    }

    private void garderLaPlusRecente(String patientId, ConstanteMesuree c) {
        enAttente.computeIfAbsent(patientId, p -> new LinkedHashMap<>())
                .merge(c.type(), c, (a, n) -> n.horodatage().isBefore(a.horodatage()) ? a : n);
    }

    /** Envoie un lot par patient ; renvoie le nombre de patients transmis. */
    @Scheduled(fixedDelayString = "${orchestrateur.constantes.periode-ms}", initialDelayString = "${orchestrateur.constantes.periode-ms}")
    public int transmettre() {
        Map<String, Map<String, ConstanteMesuree>> lot;
        synchronized (this) {
            if (enAttente.isEmpty()) {
                return 0;
            }
            lot = new LinkedHashMap<>(enAttente);
            enAttente.clear();
        }
        int transmis = 0;
        for (var e : lot.entrySet()) {
            List<ConstanteMesuree> constantes = new ArrayList<>(e.getValue().values());
            constantes.sort(Comparator.comparing(ConstanteMesuree::type));
            try {
                tableauDeBord.enregistrerConstantes(e.getKey(), constantes);
                transmis++;
            } catch (RuntimeException ex) {
                log.warn("Constantes de {} non transmises ({}) : nouvel essai au prochain envoi", e.getKey(), ex.getMessage());
                synchronized (this) {
                    constantes.forEach(c -> garderLaPlusRecente(e.getKey(), c));
                }
            }
        }
        log.info("Constantes transmises au tableau de bord pour {} patient(s)", transmis);
        return transmis;
    }

    synchronized int nombreEnAttente() {
        return enAttente.values().stream().mapToInt(Map::size).sum();
    }
}
