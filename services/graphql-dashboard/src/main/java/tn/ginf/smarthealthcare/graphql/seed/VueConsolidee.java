package tn.ginf.smarthealthcare.graphql.seed;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import tn.ginf.smarthealthcare.graphql.modele.Modele.ActionUrgence;
import tn.ginf.smarthealthcare.graphql.modele.Modele.Alerte;
import tn.ginf.smarthealthcare.graphql.modele.Modele.Constante;
import tn.ginf.smarthealthcare.graphql.modele.Modele.Couverture;
import tn.ginf.smarthealthcare.graphql.modele.Modele.FicheUrgence;
import tn.ginf.smarthealthcare.graphql.modele.Modele.Medecin;
import tn.ginf.smarthealthcare.graphql.modele.Modele.NiveauAlerte;
import tn.ginf.smarthealthcare.graphql.modele.Modele.NiveauRisque;
import tn.ginf.smarthealthcare.graphql.modele.Modele.Patient;
import tn.ginf.smarthealthcare.graphql.modele.Modele.ServiceHospitalier;
import tn.ginf.smarthealthcare.graphql.modele.Modele.StatutCouverture;
import tn.ginf.smarthealthcare.graphql.modele.Modele.StatutPriseEnCharge;
import tn.ginf.smarthealthcare.graphql.modele.Modele.StatutTraitement;
import tn.ginf.smarthealthcare.graphql.modele.Modele.Traitement;
import tn.ginf.smarthealthcare.graphql.modele.Modele.TypeMesure;

/**
 * Vue consolidée propre au tableau de bord (D5), stockée dans PostgreSQL (P2.GQL.4, D36 : JDBC + Flyway).
 * Référentiel chargé depuis contracts/seed au démarrage (lignes absentes seulement) ; alertes, fiches, constantes
 * et couvertures écrites par l'orchestrateur via les mutations. Les dates lues sont rendues dans le fuseau du service.
 */
@Component
public class VueConsolidee {

    private static final Logger log = LoggerFactory.getLogger(VueConsolidee.class);

    private final JdbcTemplate jdbc;
    private final ZoneId fuseau;

    public VueConsolidee(JdbcTemplate jdbc, ObjectMapper mapper, Clock horloge) throws IOException {
        this.jdbc = jdbc;
        this.fuseau = horloge.getZone();
        chargerSeed(mapper.copy().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    // ------------------------------------------------------------------ seed

    private void chargerSeed(ObjectMapper m) throws IOException {
        int s = 0, med = 0, p = 0, t = 0;
        for (ServiceHospitalier x : lire(m, "services-hospitaliers", ServiceHospitalier.class)) {
            s += jdbc.update("INSERT INTO service_hospitalier (code, libelle) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    x.code(), x.libelle());
        }
        for (Medecin x : lire(m, "medecins", Medecin.class)) {
            med += jdbc.update("INSERT INTO medecin (id, nom, prenom, specialite, service_code) VALUES (?, ?, ?, ?, ?) "
                    + "ON CONFLICT DO NOTHING", x.id(), x.nom(), x.prenom(), x.specialite(), x.serviceCode());
        }
        for (Patient x : lire(m, "patients", Patient.class)) {
            p += jdbc.update("INSERT INTO patient (id, nom, prenom, date_naissance, sexe, telephone, service_code, pathologie, "
                            + "niveau_risque, medecin_traitant_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                    x.id(), x.nom(), x.prenom(), Date.valueOf(x.dateNaissance()), x.sexe(), x.telephone(), x.serviceCode(),
                    x.pathologie(), x.niveauRisque().name(), x.medecinTraitantId());
        }
        for (Traitement x : lire(m, "traitements", Traitement.class)) {
            t += jdbc.update("INSERT INTO traitement (id, patient_id, medecin_id, medicament, dosage, posologie, date_debut, "
                            + "date_fin, statut) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                    x.id(), x.patientId(), x.medecinId(), x.medicament(), x.dosage(), x.posologie(),
                    Date.valueOf(x.dateDebut()), x.dateFin() == null ? null : Date.valueOf(x.dateFin()), x.statut().name());
        }
        log.info("Seed : {} service(s), {} médecin(s), {} patient(s), {} traitement(s) ajoutés (base : {} patients)",
                s, med, p, t, jdbc.queryForObject("SELECT count(*) FROM patient", Integer.class));
    }

    private static <T> List<T> lire(ObjectMapper m, String fichier, Class<T> type) throws IOException {
        CollectionType liste = m.getTypeFactory().constructCollectionType(List.class, type);
        try (InputStream in = new ClassPathResource("seed/" + fichier + ".json").getInputStream()) {
            return m.readValue(in, liste);
        }
    }

    // ------------------------------------------------------------------ lecture

    private OffsetDateTime instant(ResultSet rs, String colonne) throws SQLException {
        OffsetDateTime d = rs.getObject(colonne, OffsetDateTime.class);
        return d == null ? null : d.atZoneSameInstant(fuseau).toOffsetDateTime();
    }

    private static LocalDate jour(ResultSet rs, String colonne) throws SQLException {
        Date d = rs.getDate(colonne);
        return d == null ? null : d.toLocalDate();
    }

    private final RowMapper<Patient> patientMapper = (rs, i) -> new Patient(rs.getString("id"), rs.getString("nom"),
            rs.getString("prenom"), jour(rs, "date_naissance"), rs.getString("sexe"), rs.getString("telephone"),
            rs.getString("service_code"), rs.getString("pathologie"), NiveauRisque.valueOf(rs.getString("niveau_risque")),
            rs.getString("medecin_traitant_id"));

    private final RowMapper<Alerte> alerteMapper = (rs, i) -> new Alerte(rs.getString("id"), rs.getString("patient_id"),
            NiveauAlerte.valueOf(rs.getString("niveau")), TypeMesure.valueOf(rs.getString("type")), rs.getDouble("valeur"),
            rs.getDouble("seuil"), rs.getString("message"), instant(rs, "horodatage"));

    private final RowMapper<FicheUrgence> ficheMapper = (rs, i) -> new FicheUrgence("FIC-%06d".formatted(rs.getLong("id")),
            rs.getString("patient_id"), rs.getString("alerte_id"), StatutPriseEnCharge.valueOf(rs.getString("statut_prise_en_charge")),
            ActionUrgence.valueOf(rs.getString("action")), rs.getString("identifiant_rendez_vous"), instant(rs, "date_rendez_vous"),
            rs.getString("commentaire"), instant(rs, "creee_le"));

    public Optional<Patient> patient(String id) {
        return jdbc.query("SELECT * FROM patient WHERE id = ?", patientMapper, id).stream().findFirst();
    }

    public List<Patient> patients() {
        return jdbc.query("SELECT * FROM patient", patientMapper);
    }

    public Optional<Medecin> medecin(String id) {
        return jdbc.query("SELECT * FROM medecin WHERE id = ?", (rs, i) -> new Medecin(rs.getString("id"), rs.getString("nom"),
                rs.getString("prenom"), rs.getString("specialite"), rs.getString("service_code")), id).stream().findFirst();
    }

    public Optional<ServiceHospitalier> service(String code) {
        return services().stream().filter(s -> s.code().equals(code)).findFirst();
    }

    /** Services triés par libellé (listes déroulantes du client). */
    public List<ServiceHospitalier> services() {
        return jdbc.query("SELECT code, libelle FROM service_hospitalier ORDER BY libelle",
                (rs, i) -> new ServiceHospitalier(rs.getString("code"), rs.getString("libelle")));
    }

    public List<Traitement> traitements(String patientId) {
        return jdbc.query("SELECT * FROM traitement WHERE patient_id = ? ORDER BY date_debut, id", (rs, i) -> new Traitement(
                rs.getString("id"), rs.getString("patient_id"), rs.getString("medecin_id"), rs.getString("medicament"),
                rs.getString("dosage"), rs.getString("posologie"), jour(rs, "date_debut"), jour(rs, "date_fin"),
                StatutTraitement.valueOf(rs.getString("statut"))), patientId);
    }

    public Optional<Alerte> alerte(String id) {
        return jdbc.query("SELECT * FROM alerte WHERE id = ?", alerteMapper, id).stream().findFirst();
    }

    public List<Alerte> alertesRecentes(String patientId, int limite) {
        return jdbc.query("SELECT * FROM alerte WHERE patient_id = ? ORDER BY horodatage DESC, id DESC LIMIT ?",
                alerteMapper, patientId, limite);
    }

    public List<FicheUrgence> fiches(String patientId, int limite) {
        return jdbc.query("SELECT * FROM fiche_urgence WHERE patient_id = ? ORDER BY creee_le DESC, id DESC LIMIT ?",
                ficheMapper, patientId, limite);
    }

    public List<Constante> dernieresConstantes(String patientId) {
        return jdbc.query("SELECT * FROM constante WHERE patient_id = ?", (rs, i) -> new Constante(rs.getString("patient_id"),
                        TypeMesure.valueOf(rs.getString("type")), rs.getDouble("valeur"), instant(rs, "horodatage")), patientId)
                .stream().sorted(Comparator.comparing(Constante::type)).toList();
    }

    public Optional<Couverture> couverture(String patientId) {
        return jdbc.query("SELECT * FROM couverture WHERE patient_id = ?", (rs, i) -> new Couverture(rs.getString("patient_id"),
                StatutCouverture.valueOf(rs.getString("statut")), rs.getString("code_vignette"), rs.getInt("taux_remboursement"),
                jour(rs, "date_fin"), instant(rs, "mis_a_jour_le")), patientId).stream().findFirst();
    }

    // ------------------------------------------------------------------ écriture (mutations de l'orchestrateur)

    /** Enregistre l'alerte ; false si une alerte de même identifiant existe déjà (envoi répété : rien n'est modifié). */
    public boolean enregistrer(Alerte a) {
        return jdbc.update("INSERT INTO alerte (id, patient_id, niveau, type, valeur, seuil, message, horodatage) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING",
                a.id(), a.patientId(), a.niveau().name(), a.type().name(), a.valeur(), a.seuil(), a.message(), a.horodatage()) == 1;
    }

    /** Insère la fiche et renvoie sa version enregistrée, avec l'identifiant FIC-xxxxxx attribué par la base. */
    public FicheUrgence creerFiche(FicheUrgence f) {
        Long id = jdbc.queryForObject("INSERT INTO fiche_urgence (patient_id, alerte_id, statut_prise_en_charge, action, "
                        + "identifiant_rendez_vous, date_rendez_vous, commentaire, creee_le) VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, f.patientId(), f.alerteId(), f.statutPriseEnCharge().name(), f.action().name(),
                f.identifiantRendezVous(), f.dateRendezVous(), f.commentaire(), f.creeeLe());
        return jdbc.queryForObject("SELECT * FROM fiche_urgence WHERE id = ?", ficheMapper, id);
    }

    /** Garde, pour chaque type, la mesure la plus récente (une mesure plus ancienne arrivée en retard est ignorée). */
    public void enregistrer(Constante c) {
        jdbc.update("INSERT INTO constante (patient_id, type, valeur, horodatage) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (patient_id, type) DO UPDATE SET valeur = EXCLUDED.valeur, horodatage = EXCLUDED.horodatage "
                        + "WHERE constante.horodatage <= EXCLUDED.horodatage",
                c.patientId(), c.type().name(), c.valeur(), c.horodatage());
    }

    public void enregistrer(Couverture c) {
        jdbc.update("INSERT INTO couverture (patient_id, statut, code_vignette, taux_remboursement, date_fin, mis_a_jour_le) "
                        + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (patient_id) DO UPDATE SET statut = EXCLUDED.statut, "
                        + "code_vignette = EXCLUDED.code_vignette, taux_remboursement = EXCLUDED.taux_remboursement, "
                        + "date_fin = EXCLUDED.date_fin, mis_a_jour_le = EXCLUDED.mis_a_jour_le",
                c.patientId(), c.statut().name(), c.codeVignette(), c.tauxRemboursement(),
                c.dateFin() == null ? null : Date.valueOf(c.dateFin()), c.misAJourLe());
    }
}
