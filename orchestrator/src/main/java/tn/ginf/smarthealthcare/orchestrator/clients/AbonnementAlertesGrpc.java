package tn.ginf.smarthealthcare.orchestrator.clients;

import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.AlerteRecue;
import tn.ginf.smarthealthcare.orchestrator.workflow.WorkflowAlerte;
import tn.ginf.smarthealthcare.telemetrie.AbonnementAlertes;
import tn.ginf.smarthealthcare.telemetrie.Alerte;
import tn.ginf.smarthealthcare.telemetrie.Niveau;
import tn.ginf.smarthealthcare.telemetrie.TelemetrieGrpc;

/**
 * Étape 1 : abonnement au flux SuivreAlertes (server streaming, contracts/grpc/telemetrie.proto).
 * Chaque alerte lance une instance du workflow. Si le service gRPC est absent ou coupe le flux,
 * l'abonnement est retenté après un délai (l'ordre de démarrage des services n'a pas d'importance).
 */
@Component
public class AbonnementAlertesGrpc implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AbonnementAlertesGrpc.class);

    private final WorkflowAlerte workflow;
    private final String hote;
    private final int port;
    private final Niveau niveauMinimum;
    private final long delaiReconnexion;
    private final ZoneId fuseau;
    private final ExecutorService instances = Executors.newFixedThreadPool(4);
    private volatile boolean actif;
    private volatile ManagedChannel canal;
    private Thread ecoute;

    public AbonnementAlertesGrpc(WorkflowAlerte workflow,
                                 @Value("${orchestrateur.telemetrie.hote}") String hote,
                                 @Value("${orchestrateur.telemetrie.port}") int port,
                                 @Value("${orchestrateur.telemetrie.niveau-minimum}") String niveauMinimum,
                                 @Value("${orchestrateur.telemetrie.delai-reconnexion-secondes}") long delaiReconnexion,
                                 @Value("${orchestrateur.fuseau}") String fuseau) {
        this.workflow = workflow;
        this.hote = hote;
        this.port = port;
        this.niveauMinimum = Niveau.valueOf(niveauMinimum);
        this.delaiReconnexion = delaiReconnexion;
        this.fuseau = ZoneId.of(fuseau);
    }

    @Override
    public void start() {
        actif = true;
        ecoute = new Thread(this::ecouter, "abonnement-alertes-grpc");
        ecoute.setDaemon(true);
        ecoute.start();
    }

    private void ecouter() {
        while (actif) {
            canal = ManagedChannelBuilder.forAddress(hote, port).usePlaintext().build();
            try {
                log.info("Abonnement au flux d'alertes gRPC {}:{} (niveau minimal {})", hote, port, niveauMinimum);
                Iterator<Alerte> flux = TelemetrieGrpc.newBlockingStub(canal)
                        .suivreAlertes(AbonnementAlertes.newBuilder().setNiveauMinimum(niveauMinimum).build());
                while (flux.hasNext()) {
                    AlerteRecue alerte = convertir(flux.next());
                    instances.submit(() -> workflow.traiter(alerte));
                }
                log.warn("Flux d'alertes terminé par le service de télémétrie");
            } catch (StatusRuntimeException e) {
                if (actif) {
                    log.warn("Service de télémétrie indisponible ({}) : nouvelle tentative dans {} s",
                            e.getStatus().getCode(), delaiReconnexion);
                }
            } finally {
                canal.shutdownNow();
            }
            attendre();
        }
    }

    private void attendre() {
        try {
            TimeUnit.SECONDS.sleep(delaiReconnexion);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            actif = false;
        }
    }

    private AlerteRecue convertir(Alerte a) {
        Timestamp t = a.getHorodatage();
        OffsetDateTime horodatage = Instant.ofEpochSecond(t.getSeconds()).atZone(fuseau).toOffsetDateTime(); // à la seconde, comme les autres dates
        return new AlerteRecue(a.getIdentifiantAlerte(), a.getIdentifiantPatient(), a.getNiveau().name(),
                a.getType().name(), a.getValeur(), a.getSeuil(), a.getMessage(), horodatage);
    }

    @Override
    public void stop() {
        actif = false;
        if (canal != null) {
            canal.shutdownNow();
        }
        if (ecoute != null) {
            ecoute.interrupt();
        }
        instances.shutdown();
    }

    @Override
    public boolean isRunning() {
        return actif;
    }
}
