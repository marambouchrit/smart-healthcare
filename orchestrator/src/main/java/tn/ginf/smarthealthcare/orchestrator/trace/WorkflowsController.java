package tn.ginf.smarthealthcare.orchestrator.trace;

import java.net.URI;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.ginf.smarthealthcare.orchestrator.trace.DepotTraces.Workflow;
import tn.ginf.smarthealthcare.orchestrator.trace.DepotTraces.WorkflowResume;

/**
 * Lecture de la trace des workflows (P5.3, D54), contrat contracts/orchestrateur/workflows-openapi.yaml ;
 * exposée par la gateway sur {@code /orchestrator/**} avec jeton JWT.
 */
@RestController
@RequestMapping("/orchestrator/workflows")
public class WorkflowsController {

    private static final Set<String> ETATS = Set.of("EN_COURS", "TERMINE", "AMBULANCE", "ECHEC");

    private final DepotTraces depot;

    public WorkflowsController(DepotTraces depot) {
        this.depot = depot;
    }

    @GetMapping
    public List<WorkflowResume> listerWorkflows(@RequestParam(required = false) String patientId,
                                                @RequestParam(required = false) String etat,
                                                @RequestParam(required = false) String limite) {
        if (patientId != null && !patientId.matches("^TN-[0-9]{7}$")) {
            throw probleme(HttpStatus.BAD_REQUEST, "Le paramètre patientId doit être TN- suivi de 7 chiffres.");
        }
        if (etat != null && !ETATS.contains(etat)) {
            throw probleme(HttpStatus.BAD_REQUEST, "Le paramètre etat doit valoir EN_COURS, TERMINE, AMBULANCE ou ECHEC.");
        }
        int n = 50;
        if (limite != null) {
            try {
                n = Integer.parseInt(limite);
            } catch (NumberFormatException e) {
                n = -1;
            }
            if (n < 1 || n > 200) {
                throw probleme(HttpStatus.BAD_REQUEST, "Le paramètre limite doit être un entier de 1 à 200.");
            }
        }
        return depot.lister(patientId, etat, n);
    }

    @GetMapping("/{id}")
    public Workflow obtenirWorkflow(@PathVariable String id) {
        if (id.matches("^WF-[0-9]{6}$")) {
            var trouve = depot.obtenir(Long.parseLong(id.substring(3)));
            if (trouve.isPresent()) {
                return trouve.get();
            }
        }
        throw probleme(HttpStatus.NOT_FOUND, "Aucune instance de workflow " + id + ".");
    }

    private static ErrorResponseException probleme(HttpStatus statut, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(statut, detail);
        p.setType(URI.create(statut == HttpStatus.NOT_FOUND ? "/problemes/non-trouve" : "/problemes/requete-invalide"));
        p.setTitle(statut == HttpStatus.NOT_FOUND ? "Ressource introuvable" : "Requête invalide");
        return new ErrorResponseException(statut, p, null);
    }
}
