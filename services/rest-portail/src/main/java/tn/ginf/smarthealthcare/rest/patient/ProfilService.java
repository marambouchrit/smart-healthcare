package tn.ginf.smarthealthcare.rest.patient;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.ginf.smarthealthcare.rest.seed.SeedRepository;
import tn.ginf.smarthealthcare.rest.seed.SeedRepository.Adresse;
import tn.ginf.smarthealthcare.rest.seed.SeedRepository.Patient;
import tn.ginf.smarthealthcare.rest.web.Problemes.NonTrouveException;
import tn.ginf.smarthealthcare.rest.web.Problemes.RequeteInvalideException;

/** Profils patients (P2.REST.4) : état initial = contracts/seed/patients.json, coordonnées modifiables en mémoire. */
@Service
public class ProfilService {

    private static final Logger log = LoggerFactory.getLogger(ProfilService.class);

    private final Map<String, Patient> profils = new LinkedHashMap<>();

    public ProfilService(SeedRepository seed) {
        seed.patients().forEach(p -> profils.put(p.id(), p));
    }

    public synchronized Patient obtenir(String id) {
        Patient p = profils.get(id);
        if (p == null) {
            throw new NonTrouveException("Aucun patient " + id + ".");
        }
        return p;
    }

    /** Modifie seulement les coordonnées fournies (les champs absents sont inchangés). */
    public synchronized Patient modifierCoordonnees(String id, String telephone, String email, Adresse adresse) {
        if (telephone == null && email == null && adresse == null) {
            throw new RequeteInvalideException("Indiquer au moins une coordonnée à modifier (telephone, email, adresse).");
        }
        Patient p = obtenir(id);
        Patient maj = new Patient(p.id(), p.nom(), p.prenom(), p.dateNaissance(), p.sexe(),
                telephone != null ? telephone : p.telephone(), email != null ? email : p.email(),
                adresse != null ? adresse : p.adresse(), p.medecinTraitantId());
        profils.put(id, maj);
        log.info("Profil {} mis à jour :{}{}{}", id, telephone != null ? " téléphone" : "", email != null ? " email" : "",
                adresse != null ? " adresse" : "");
        return maj;
    }
}
