package tn.ginf.smarthealthcare.orchestrator.clients;

import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tn.ginf.smarthealthcare.orchestrator.workflow.RelaisConstantes;
import tn.ginf.smarthealthcare.telemetrie.AbonnementMesures;
import tn.ginf.smarthealthcare.telemetrie.Mesure;
import tn.ginf.smarthealthcare.telemetrie.TelemetrieGrpc;

/**
 * Abonnement au flux SuivreMesures (P2.GRPC.5) : chaque mesure est confiée au {@link RelaisConstantes}.
 * Même principe de reconnexion que l'abonnement aux alertes.
 */
@Component
public class AbonnementMesuresGrpc implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AbonnementMesuresGrpc.class);

    private final RelaisConstantes relais;
    private final String hote;
    private final int port;
    private final long delaiReconnexion;
    private final ZoneId fuseau;
    private volatile boolean actif;
    private volatile ManagedChannel canal;
    private Thread ecoute;

    public AbonnementMesuresGrpc(RelaisConstantes relais,
                                 @Value("${orchestrateur.telemetrie.hote}") String hote,
                                 @Value("${orchestrateur.telemetrie.port}") int port,
                                 @Value("${orchestrateur.telemetrie.delai-reconnexion-secondes}") long delaiReconnexion,
                                 @Value("${orchestrateur.fuseau}") String fuseau) {
        this.relais = relais;
        this.hote = hote;
        this.port = port;
        this.delaiReconnexion = delaiReconnexion;
        this.fuseau = ZoneId.of(fuseau);
    }

    @Override
    public void start() {
        actif = true;
        ecoute = new Thread(this::ecouter, "abonnement-mesures-grpc");
        ecoute.setDaemon(true);
        ecoute.start();
    }

    private void ecouter() {
        while (actif) {
            canal = ManagedChannelBuilder.forAddress(hote, port).usePlaintext().build();
            try {
                log.info("Abonnement au flux de mesures gRPC {}:{}", hote, port);
                Iterator<Mesure> flux = TelemetrieGrpc.newBlockingStub(canal).suivreMesures(AbonnementMesures.getDefaultInstance());
                while (flux.hasNext()) {
                    Mesure m = flux.next();
                    Timestamp t = m.getHorodatage();
                    relais.recevoir(m.getIdentifiantPatient(), m.getType().name(), m.getValeur(),
                            Instant.ofEpochSecond(t.getSeconds()).atZone(fuseau).toOffsetDateTime()); // à la seconde
                }
            } catch (StatusRuntimeException e) {
                if (actif) {
                    log.warn("Flux de mesures indisponible ({}) : nouvelle tentative dans {} s", e.getStatus().getCode(), delaiReconnexion);
                }
            } finally {
                canal.shutdownNow();
            }
            try {
                TimeUnit.SECONDS.sleep(delaiReconnexion);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                actif = false;
            }
        }
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
    }

    @Override
    public boolean isRunning() {
        return actif;
    }
}
