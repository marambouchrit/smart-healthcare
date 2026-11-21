package tn.ginf.smarthealthcare.rest.creneau;

import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/creneaux (contrat : operationId listerCreneaux). */
@RestController
@RequestMapping("/api/creneaux")
public class CreneauController {

    private final CreneauService creneaux;

    public CreneauController(CreneauService creneaux) {
        this.creneaux = creneaux;
    }

    @GetMapping
    public List<Creneau> listerCreneaux(
            @RequestParam(required = false) @Pattern(regexp = "^MED-[0-9]{3}$") String medecinId,
            @RequestParam(required = false) String serviceCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "true") boolean disponible) {
        return creneaux.lister(medecinId, serviceCode, date, disponible);
    }
}
