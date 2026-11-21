package tn.ginf.smarthealthcare.rest.patient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.ginf.smarthealthcare.rest.persistance.Depots;
import tn.ginf.smarthealthcare.rest.persistance.Entites.PatientEntite;
import tn.ginf.smarthealthcare.rest.seed.SeedRepository;
import tn.ginf.smarthealthcare.rest.seed.SeedRepository.Adresse;
import tn.ginf.smarthealthcare.rest.seed.SeedRepository.Patient;
import tn.ginf.smarthealthcare.rest.web.Problemes.NonTrouveException;
import tn.ginf.smarthealthcare.rest.web.Problemes.RequeteInvalideException;

/** Profils patients (P2.REST.4) enregistrés dans PostgreSQL (P2.REST.7) : seules les coordonnées sont modifiables. */
@Service
public class ProfilService {

    private static final Logger log = LoggerFactory.getLogger(ProfilService.class);

    private final SeedRepository seed;
    private final Depots.Patients patients;

    public ProfilService(SeedRepository seed, Depots.Patients patients) {
        this.seed = seed;
        this.patients = patients;
    }

    @Transactional(readOnly = true)
    public Patient obtenir(String id) {
        return seed.patient(id).orElseThrow(() -> new NonTrouveException("Aucun patient " + id + "."));
    }

    /** Modifie seulement les coordonnées fournies (les champs absents sont inchangés). */
    @Transactional
    public Patient modifierCoordonnees(String id, String telephone, String email, Adresse adresse) {
        if (telephone == null && email == null && adresse == null) {
            throw new RequeteInvalideException("Indiquer au moins une coordonnée à modifier (telephone, email, adresse).");
        }
        PatientEntite p = patients.findById(id).orElseThrow(() -> new NonTrouveException("Aucun patient " + id + "."));
        if (telephone != null) {
            p.telephone = telephone;
        }
        if (email != null) {
            p.email = email;
        }
        if (adresse != null) {
            p.rue = adresse.rue();
            p.ville = adresse.ville();
            p.codePostal = adresse.codePostal();
        }
        patients.saveAndFlush(p);
        log.info("Profil {} mis à jour :{}{}{}", id, telephone != null ? " téléphone" : "", email != null ? " email" : "",
                adresse != null ? " adresse" : "");
        return obtenir(id);
    }
}
