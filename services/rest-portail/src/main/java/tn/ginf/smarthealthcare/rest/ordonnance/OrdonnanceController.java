package tn.ginf.smarthealthcare.rest.ordonnance;

import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.ginf.smarthealthcare.rest.ordonnance.OrdonnanceService.Ordonnance;

/** GET /api/patients/{patientId}/ordonnances (contrat : operationId listerOrdonnances). */
@RestController
public class OrdonnanceController {

    public enum StatutTraitement { EN_COURS, TERMINE }

    private final OrdonnanceService service;

    public OrdonnanceController(OrdonnanceService service) {
        this.service = service;
    }

    @GetMapping("/api/patients/{patientId}/ordonnances")
    public List<Ordonnance> listerOrdonnances(@PathVariable @Pattern(regexp = "^TN-[0-9]{7}$") String patientId,
                                              @RequestParam(required = false) StatutTraitement statut) {
        return service.duPatient(patientId, statut == null ? null : statut.name());
    }
}
