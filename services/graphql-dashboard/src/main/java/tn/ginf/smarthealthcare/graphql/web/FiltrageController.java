package tn.ginf.smarthealthcare.graphql.web;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import tn.ginf.smarthealthcare.graphql.modele.Modele.FiltresPatients;
import tn.ginf.smarthealthcare.graphql.modele.Modele.Patient;
import tn.ginf.smarthealthcare.graphql.modele.Modele.ServiceHospitalier;
import tn.ginf.smarthealthcare.graphql.seed.VueConsolidee;

/**
 * P2.GQL.2 : query patients(filtres) et services (D30).
 * Critères combinés par ET ; pathologie = recherche partielle sans casse ni accents ; liste niveauRisque = l'un des
 * niveaux ; tri : risque décroissant puis nom. Les champs du dossier se résolvent comme pour patient(id).
 */
@Controller
public class FiltrageController {

    private static final Logger log = LoggerFactory.getLogger(FiltrageController.class);

    private final VueConsolidee vue;

    public FiltrageController(VueConsolidee vue) {
        this.vue = vue;
    }

    @QueryMapping
    public List<Patient> patients(@Argument FiltresPatients filtres) {
        FiltresPatients f = filtres == null ? new FiltresPatients(null, null, null) : filtres;
        String recherche = f.pathologie() == null ? null : normaliser(f.pathologie());
        List<Patient> resultat = vue.patients().stream()
                .filter(p -> f.serviceCode() == null || p.serviceCode().equals(f.serviceCode()))
                .filter(p -> recherche == null || normaliser(p.pathologie()).contains(recherche))
                .filter(p -> f.niveauRisque() == null || f.niveauRisque().isEmpty() || f.niveauRisque().contains(p.niveauRisque()))
                .sorted(Comparator.comparing(Patient::niveauRisque).reversed().thenComparing(Patient::nom))
                .toList();
        log.info("patients(service={}, pathologie={}, risque={}) -> {} patient(s)",
                f.serviceCode(), f.pathologie(), f.niveauRisque(), resultat.size());
        return resultat;
    }

    @QueryMapping
    public List<ServiceHospitalier> services() {
        return vue.services();
    }

    /** Minuscules sans accents : « BPCO » = « bpco », « Diabète » = « diabete ». */
    static String normaliser(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
