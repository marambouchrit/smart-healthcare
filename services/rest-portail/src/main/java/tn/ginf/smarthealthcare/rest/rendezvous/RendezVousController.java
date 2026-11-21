package tn.ginf.smarthealthcare.rest.rendezvous;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CRUD /api/rendez-vous (contrat : listerRendezVous, creerRendezVous, obtenirRendezVous, remplacerRendezVous,
 * modifierRendezVous, supprimerRendezVous). */
@RestController
@RequestMapping("/api/rendez-vous")
public class RendezVousController {

    /** Schéma « RendezVousCreation » (POST et PUT). */
    public record RendezVousCreation(
            @NotNull @Pattern(regexp = "^TN-[0-9]{7}$") String patientId,
            @NotNull @Pattern(regexp = "^CRN-[0-9]{6}$") String creneauId,
            @Size(max = 500) String motif) {}

    /** Schéma « RendezVousModification » (PATCH, au moins un champ). */
    public record RendezVousModification(@Size(max = 500) String motif, RendezVous.Statut statut) {}

    private static final String ID = "^RDV-[0-9]{6}$";

    private final RendezVousService service;

    public RendezVousController(RendezVousService service) {
        this.service = service;
    }

    @GetMapping
    public List<RendezVous> listerRendezVous(
            @RequestParam(required = false) @Pattern(regexp = "^TN-[0-9]{7}$") String patientId,
            @RequestParam(required = false) @Pattern(regexp = "^MED-[0-9]{3}$") String medecinId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.lister(patientId, medecinId, date);
    }

    @PostMapping
    public ResponseEntity<RendezVous> creerRendezVous(@Valid @RequestBody RendezVousCreation c) {
        RendezVous r = service.creer(c.patientId(), c.creneauId(), c.motif());
        return ResponseEntity.created(URI.create("/api/rendez-vous/" + r.id())).body(r);
    }

    @GetMapping("/{id}")
    public RendezVous obtenirRendezVous(@PathVariable @Pattern(regexp = ID) String id) {
        return service.obtenir(id);
    }

    @PutMapping("/{id}")
    public RendezVous remplacerRendezVous(@PathVariable @Pattern(regexp = ID) String id,
                                          @Valid @RequestBody RendezVousCreation c) {
        return service.remplacer(id, c.patientId(), c.creneauId(), c.motif());
    }

    @PatchMapping("/{id}")
    public RendezVous modifierRendezVous(@PathVariable @Pattern(regexp = ID) String id,
                                         @Valid @RequestBody RendezVousModification m) {
        return service.modifier(id, m.motif(), m.statut());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimerRendezVous(@PathVariable @Pattern(regexp = ID) String id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
