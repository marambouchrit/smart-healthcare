package tn.ginf.smarthealthcare.orchestrator.clients;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.AlerteRecue;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.RendezVousUrgence;

/** Étape 4 : mutations du contrat contracts/graphql/dashboard.graphqls, envoyées en HTTP POST avec variables. */
@Component
public class ClientTableauDeBordGraphql implements Ports.TableauDeBord {

    private static final String ENREGISTRER_ALERTE =
            "mutation($a: AlerteInput!) { enregistrerAlerte(alerte: $a) { id } }";
    private static final String METTRE_A_JOUR_COUVERTURE =
            "mutation($c: CouvertureInput!) { mettreAJourCouverture(couverture: $c) { statut } }";
    private static final String ENREGISTRER_CONSTANTES =
            "mutation($p: ID!, $c: [ConstanteInput!]!) { enregistrerConstantes(identifiantPatient: $p, constantes: $c) { type } }";
    private static final String CREER_FICHE =
            "mutation($f: FicheUrgenceInput!) { creerFicheUrgence(fiche: $f) { id } }";

    private final RestClient http;

    public ClientTableauDeBordGraphql(RestClient.Builder builder, @Value("${orchestrateur.tableau-de-bord.url}") String url) {
        this.http = builder.baseUrl(url).build();
    }

    @Override
    public void enregistrerAlerte(AlerteRecue a) {
        Map<String, Object> entree = new LinkedHashMap<>();
        entree.put("identifiantAlerte", a.id());
        entree.put("identifiantPatient", a.patientId());
        entree.put("niveau", a.niveau());
        entree.put("type", a.type());
        entree.put("valeur", a.valeur());
        entree.put("seuil", a.seuil());
        entree.put("message", a.message());
        entree.put("horodatage", iso(a.horodatage()));
        executer(ENREGISTRER_ALERTE, Map.of("a", entree));
    }

    @Override
    public void mettreAJourCouverture(String patientId, Ports.Droits d) {
        Map<String, Object> entree = new LinkedHashMap<>();
        entree.put("identifiantPatient", patientId);
        entree.put("statut", d.statut());
        entree.put("codeVignette", d.codeVignette());
        entree.put("tauxRemboursement", d.tauxRemboursement() == null ? 0 : d.tauxRemboursement()); // non affilié : 0
        entree.put("dateFin", d.dateFin() == null ? null : d.dateFin().toString());
        executer(METTRE_A_JOUR_COUVERTURE, Map.of("c", entree));
    }

    @Override
    public String creerFicheUrgence(AlerteRecue a, boolean aRegulariser, String commentaire, RendezVousUrgence rdv,
                                    Ports.InterventionAmbulance ambulance) {
        Map<String, Object> entree = new LinkedHashMap<>();
        entree.put("identifiantPatient", a.patientId());
        entree.put("identifiantAlerte", a.id());
        entree.put("statutPriseEnCharge", aRegulariser ? "A_REGULARISER" : "PRISE_EN_CHARGE");
        // ActionUrgence du contrat : identifiant et date du rendez-vous absents si ambulance (P5.2)
        entree.put("action", rdv != null ? "CONSULTATION_URGENCE" : "AMBULANCE");
        if (rdv != null) {
            entree.put("identifiantRendezVous", rdv.id());
            entree.put("dateRendezVous", iso(rdv.debut()));
        }
        if (commentaire != null) {
            entree.put("commentaire", commentaire);
        }
        Map<String, Object> data = executer(CREER_FICHE, Map.of("f", entree));
        @SuppressWarnings("unchecked")
        Map<String, Object> fiche = (Map<String, Object>) data.get("creerFicheUrgence");
        return (String) fiche.get("id");
    }

    @Override
    public void enregistrerConstantes(String patientId, List<Ports.ConstanteMesuree> constantes) {
        List<Map<String, Object>> entrees = constantes.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", c.type());
            m.put("valeur", c.valeur());
            m.put("horodatage", iso(c.horodatage()));
            return m;
        }).toList();
        executer(ENREGISTRER_CONSTANTES, Map.of("p", patientId, "c", entrees));
    }

    private static String iso(OffsetDateTime d) {
        return d.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executer(String document, Map<String, Object> variables) {
        Map<String, Object> reponse;
        try {
            reponse = http.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("query", document, "variables", variables))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (RuntimeException e) {
            throw Pannes.traduire("GraphQL", e);
        }
        List<Map<String, Object>> erreurs = (List<Map<String, Object>>) reponse.get("errors");
        if (erreurs != null && !erreurs.isEmpty()) {
            throw new IllegalStateException("GraphQL : " + erreurs.get(0).get("message"));
        }
        return (Map<String, Object>) reponse.get("data");
    }
}
