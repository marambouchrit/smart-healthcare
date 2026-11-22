package tn.ginf.smarthealthcare.orchestrator.clients;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.AlerteRecue;

/**
 * Étape 3 (contracts/rest/portail-openapi.yaml) : POST /api/urgences, 409 = aucun créneau libre ;
 * POST /api/ambulances dans ce cas (P5.2).
 */
@Component
public class ClientPortailRest implements Ports.Portail {

    private final RestClient http;

    public ClientPortailRest(RestClient.Builder builder, @Value("${orchestrateur.portail.url}") String url) {
        this.http = builder.baseUrl(url).build();
    }

    /** Schéma DemandeUrgence, commun aux deux opérations. */
    private static Map<String, Object> demande(AlerteRecue alerte, boolean aRegulariser) {
        Map<String, Object> demande = new LinkedHashMap<>();
        demande.put("patientId", alerte.patientId());
        demande.put("identifiantAlerte", alerte.id());
        demande.put("niveau", alerte.niveau());
        demande.put("motif", alerte.message());
        demande.put("aRegulariser", aRegulariser);
        return demande;
    }

    @Override
    public Ports.InterventionAmbulance envoyerAmbulance(AlerteRecue alerte, boolean aRegulariser) {
        Map<String, Object> intervention;
        try {
            intervention = http.post().uri("/api/ambulances")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(demande(alerte, aRegulariser))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (RuntimeException e) {
            throw Pannes.traduire("REST", e);
        }
        return new Ports.InterventionAmbulance((String) intervention.get("id"),
                OffsetDateTime.parse((String) intervention.get("arriveeEstimee")));
    }

    @Override
    public Optional<Ports.RendezVousUrgence> reserverUrgence(AlerteRecue alerte, boolean aRegulariser) {
        Map<String, Object> demande = demande(alerte, aRegulariser);
        try {
            Map<String, Object> rdv = http.post().uri("/api/urgences")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(demande)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            Map<String, Object> creneau = (Map<String, Object>) rdv.get("creneau");
            return Optional.of(new Ports.RendezVousUrgence((String) rdv.get("id"),
                    OffsetDateTime.parse((String) creneau.get("debut"))));
        } catch (HttpClientErrorException.Conflict e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            throw Pannes.traduire("REST", e);
        }
    }
}
