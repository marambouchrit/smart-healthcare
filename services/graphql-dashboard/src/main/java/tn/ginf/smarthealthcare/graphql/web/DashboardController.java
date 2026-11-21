package tn.ginf.smarthealthcare.graphql.web;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import tn.ginf.smarthealthcare.graphql.modele.Modele.Alerte;
import tn.ginf.smarthealthcare.graphql.modele.Modele.AlerteInput;
import tn.ginf.smarthealthcare.graphql.modele.Modele.Constante;
import tn.ginf.smarthealthcare.graphql.modele.Modele.ConstanteInput;
import tn.ginf.smarthealthcare.graphql.modele.Modele.Couverture;
import tn.ginf.smarthealthcare.graphql.modele.Modele.CouvertureInput;
import tn.ginf.smarthealthcare.graphql.modele.Modele.FicheUrgence;
import tn.ginf.smarthealthcare.graphql.modele.Modele.FicheUrgenceInput;
import tn.ginf.smarthealthcare.graphql.modele.Modele.Medecin;
import tn.ginf.smarthealthcare.graphql.modele.Modele.Patient;
import tn.ginf.smarthealthcare.graphql.modele.Modele.ServiceHospitalier;
import tn.ginf.smarthealthcare.graphql.modele.Modele.StatutTraitement;
import tn.ginf.smarthealthcare.graphql.modele.Modele.Traitement;
import tn.ginf.smarthealthcare.graphql.seed.VueConsolidee;
import tn.ginf.smarthealthcare.graphql.web.Erreurs.NonTrouve;

/**
 * Query patient(id) (dossier consolidé, P2.GQL.1) ; mutations de l'orchestrateur : enregistrerAlerte, creerFicheUrgence,
 * enregistrerConstantes (D18), mettreAJourCouverture.
 * patients(filtres), services et historiqueSoins : P2.GQL.2 et P2.GQL.3.
 */
@Controller
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final VueConsolidee vue;
    private final Clock horloge;

    public DashboardController(VueConsolidee vue, Clock horloge) {
        this.vue = vue;
        this.horloge = horloge;
    }

    // ---------------- Query ----------------

    @QueryMapping
    public Patient patient(@Argument String id) {
        return vue.patient(id).orElse(null);
    }

    // ---------------- Mutations ----------------

    @MutationMapping
    public Alerte enregistrerAlerte(@Argument AlerteInput alerte) {
        exigerPatient(alerte.identifiantPatient());
        Alerte a = new Alerte(alerte.identifiantAlerte(), alerte.identifiantPatient(), alerte.niveau(), alerte.type(),
                alerte.valeur(), alerte.seuil(), alerte.message(), alerte.horodatage());
        vue.enregistrer(a);
        log.info("enregistrerAlerte {} patient={} niveau={}", a.id(), a.patientId(), a.niveau());
        return a;
    }

    @MutationMapping
    public synchronized FicheUrgence creerFicheUrgence(@Argument FicheUrgenceInput fiche) {
        exigerPatient(fiche.identifiantPatient());
        Alerte alerte = vue.alerte(fiche.identifiantAlerte())
                .orElseThrow(() -> new NonTrouve("Alerte inconnue : " + fiche.identifiantAlerte()));
        if (!alerte.patientId().equals(fiche.identifiantPatient())) {
            throw new Erreurs.Invalide("L'alerte " + alerte.id() + " ne concerne pas le patient " + fiche.identifiantPatient());
        }
        FicheUrgence f = new FicheUrgence("FIC-%06d".formatted(vue.nombreFiches() + 1), fiche.identifiantPatient(),
                alerte.id(), fiche.statutPriseEnCharge(), fiche.action(), fiche.identifiantRendezVous(),
                fiche.dateRendezVous(), fiche.commentaire(), OffsetDateTime.now(horloge).withNano(0));
        vue.enregistrer(f);
        log.info("creerFicheUrgence {} patient={} alerte={} action={}", f.id(), f.patientId(), f.alerteId(), f.action());
        return f;
    }

    @MutationMapping
    public List<Constante> enregistrerConstantes(@Argument String identifiantPatient, @Argument List<ConstanteInput> constantes) {
        exigerPatient(identifiantPatient);
        if (constantes.isEmpty()) {
            throw new Erreurs.Invalide("La liste des constantes est vide.");
        }
        constantes.forEach(c -> vue.enregistrer(new Constante(identifiantPatient, c.type(), c.valeur(), c.horodatage())));
        log.info("enregistrerConstantes patient={} : {}", identifiantPatient,
                constantes.stream().map(c -> c.type() + "=" + c.valeur()).toList());
        return vue.dernieresConstantes(identifiantPatient);
    }

    @MutationMapping
    public Couverture mettreAJourCouverture(@Argument CouvertureInput couverture) {
        exigerPatient(couverture.identifiantPatient());
        if (couverture.tauxRemboursement() < 0 || couverture.tauxRemboursement() > 100) {
            throw new Erreurs.Invalide("Taux de remboursement hors de 0..100 : " + couverture.tauxRemboursement());
        }
        Couverture c = new Couverture(couverture.identifiantPatient(), couverture.statut(), couverture.codeVignette(),
                couverture.tauxRemboursement(), couverture.dateFin(), OffsetDateTime.now(horloge).withNano(0));
        vue.enregistrer(c);
        log.info("mettreAJourCouverture patient={} statut={} taux={} %", c.patientId(), c.statut(), c.tauxRemboursement());
        return c;
    }

    private void exigerPatient(String id) {
        vue.patient(id).orElseThrow(() -> new NonTrouve("Patient inconnu : " + id));
    }

    // ---------------- Champs calculés ----------------

    @SchemaMapping
    public ServiceHospitalier service(Patient p) { return vue.service(p.serviceCode()).orElseThrow(); }

    @SchemaMapping
    public Medecin medecinTraitant(Patient p) { return vue.medecin(p.medecinTraitantId()).orElse(null); }

    /** Dernière valeur de chaque type de mesure (enregistrerConstantes, D18). */
    @SchemaMapping
    public List<Constante> dernieresConstantes(Patient p) { return vue.dernieresConstantes(p.id()); }

    /** Dernière couverture CNAM transmise par l'orchestrateur ; null si jamais vérifiée. */
    @SchemaMapping
    public Couverture couverture(Patient p) { return vue.couverture(p.id()).orElse(null); }

    @SchemaMapping
    public List<Alerte> alertesRecentes(Patient p, @Argument int limite) { return vue.alertesRecentes(p.id(), limite); }

    @SchemaMapping
    public List<FicheUrgence> fichesUrgence(Patient p, @Argument int limite) { return vue.fiches(p.id(), limite); }

    @SchemaMapping
    public List<Traitement> traitementsEnCours(Patient p) {
        return vue.traitements(p.id()).stream().filter(t -> t.statut() == StatutTraitement.EN_COURS).toList();
    }

    @SchemaMapping(typeName = "Medecin", field = "service")
    public ServiceHospitalier serviceDuMedecin(Medecin m) { return vue.service(m.serviceCode()).orElseThrow(); }

    @SchemaMapping
    public Medecin prescripteur(Traitement t) { return vue.medecin(t.medecinId()).orElseThrow(); }

    @SchemaMapping(typeName = "FicheUrgence", field = "patient")
    public Patient patientDeLaFiche(FicheUrgence f) { return vue.patient(f.patientId()).orElseThrow(); }

    @SchemaMapping(typeName = "FicheUrgence", field = "alerte")
    public Alerte alerteDeLaFiche(FicheUrgence f) { return vue.alerte(f.alerteId()).orElseThrow(); }
}
