package tn.ginf.smarthealthcare.rest.patient;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.ginf.smarthealthcare.rest.seed.SeedRepository.Adresse;
import tn.ginf.smarthealthcare.rest.seed.SeedRepository.Patient;

/** GET / PATCH /api/patients/{patientId} (contrat : obtenirProfil, modifierProfil). */
@RestController
@RequestMapping("/api/patients/{patientId}")
public class ProfilController {

    /** Schéma « Adresse » : les trois champs sont obligatoires. */
    public record AdresseEntree(@NotBlank String rue, @NotBlank String ville,
                                @NotBlank @Pattern(regexp = "^[0-9]{4}$") String codePostal) {}

    /** Schéma « ProfilModification » : seules les coordonnées sont modifiables (au moins un champ). */
    public record ProfilModification(@Pattern(regexp = "^\\+?[0-9 ]{8,20}$") String telephone,
                                     @Email String email,
                                     @Valid AdresseEntree adresse) {}

    private static final String ID = "^TN-[0-9]{7}$";

    private final ProfilService service;

    public ProfilController(ProfilService service) {
        this.service = service;
    }

    @GetMapping
    public Patient obtenirProfil(@PathVariable @Pattern(regexp = ID) String patientId) {
        return service.obtenir(patientId);
    }

    @PatchMapping
    public Patient modifierProfil(@PathVariable @Pattern(regexp = ID) String patientId,
                                  @Valid @RequestBody ProfilModification m) {
        Adresse adresse = m.adresse() == null ? null : new Adresse(m.adresse().rue(), m.adresse().ville(), m.adresse().codePostal());
        return service.modifierCoordonnees(patientId, m.telephone(), m.email(), adresse);
    }
}
