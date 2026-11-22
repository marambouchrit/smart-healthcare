package tn.ginf.smarthealthcare.rest.urgence;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /api/ambulances} du contrat OpenAPI (operationId envoyerAmbulance) : 201, 400, 404. */
@RestController
@RequestMapping("/api/ambulances")
public class AmbulanceController {

    private final AmbulanceService ambulances;

    public AmbulanceController(AmbulanceService ambulances) {
        this.ambulances = ambulances;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InterventionAmbulance envoyerAmbulance(@Valid @RequestBody DemandeUrgence demande) {
        return ambulances.envoyer(demande);
    }
}
