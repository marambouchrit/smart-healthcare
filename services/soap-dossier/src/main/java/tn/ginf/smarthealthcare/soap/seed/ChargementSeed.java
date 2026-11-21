package tn.ginf.smarthealthcare.soap.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import tn.ginf.smarthealthcare.soap.persistance.CouvertureEntite;
import tn.ginf.smarthealthcare.soap.persistance.Depots;
import tn.ginf.smarthealthcare.soap.persistance.LettreCleEntite;
import tn.ginf.smarthealthcare.soap.persistance.MedecinEntite;
import tn.ginf.smarthealthcare.soap.persistance.PatientEntite;
import tn.ginf.smarthealthcare.soap.persistance.TraitementEntite;

/**
 * Charge contracts/seed (copié sous seed/ dans le classpath) dans PostgreSQL au démarrage (D35) :
 * seules les lignes absentes sont insérées, donc un redémarrage ne crée pas de doublon et ne touche pas
 * aux feuilles de soins déjà émises.
 */
@Component
public class ChargementSeed {

    private static final Logger log = LoggerFactory.getLogger(ChargementSeed.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Identifiant(String id) {}

    record Couverture(String patientId, String codeVignette, String filiere, LocalDate dateDebut, LocalDate dateFin,
                      int tauxRemboursement) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LettreCle(String code, String libelle, BigDecimal valeurUnitaireTND) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Traitement(String id, String patientId, String medecinId, String medicament, String dosage, LocalDate dateDebut) {}

    public ChargementSeed(ObjectMapper mapper, Depots.Patients patients, Depots.Medecins medecins,
                          Depots.Couvertures couvertures, Depots.LettresCles lettres, Depots.Traitements traitements)
            throws IOException {
        int p = inserer(patients, lire(mapper, "patients", new TypeReference<List<Identifiant>>() {}),
                Identifiant::id, i -> new PatientEntite(i.id()));
        int m = inserer(medecins, lire(mapper, "medecins", new TypeReference<List<Identifiant>>() {}),
                Identifiant::id, i -> new MedecinEntite(i.id()));
        int l = inserer(lettres, lire(mapper, "lettres-cles", new TypeReference<List<LettreCle>>() {}),
                LettreCle::code, x -> new LettreCleEntite(x.code(), x.libelle(), x.valeurUnitaireTND()));
        int c = inserer(couvertures, lire(mapper, "couvertures-cnam", new TypeReference<List<Couverture>>() {}),
                Couverture::patientId, x -> new CouvertureEntite(x.patientId(), x.codeVignette(), x.filiere(),
                        x.dateDebut(), x.dateFin(), x.tauxRemboursement()));
        int t = inserer(traitements, lire(mapper, "traitements", new TypeReference<List<Traitement>>() {}),
                Traitement::id, x -> new TraitementEntite(x.id(), x.patientId(), x.medecinId(), x.medicament(),
                        x.dosage(), x.dateDebut()));
        log.info("Seed : {} patient(s), {} médecin(s), {} lettre(s)-clé(s), {} couverture(s), {} traitement(s) ajoutés "
                        + "(base : {} patients, {} couvertures)", p, m, l, c, t, patients.count(), couvertures.count());
    }

    private static <T> List<T> lire(ObjectMapper mapper, String fichier, TypeReference<List<T>> type) throws IOException {
        try (InputStream in = new ClassPathResource("seed/" + fichier + ".json").getInputStream()) {
            return mapper.readValue(in, type);
        }
    }

    /** Insère les éléments dont l'identifiant est absent ; renvoie le nombre d'insertions (saveAll est transactionnel). */
    <S, E, ID> int inserer(JpaRepository<E, ID> depot, List<S> source, Function<S, ID> id, Function<S, E> entite) {
        List<E> nouveaux = source.stream().filter(s -> !depot.existsById(id.apply(s))).map(entite).toList();
        depot.saveAll(nouveaux);
        return nouveaux.size();
    }
}
