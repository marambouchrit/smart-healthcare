package tn.ginf.smarthealthcare.orchestrator.trace;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.AlerteRecue;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.EtapeTrace;
import tn.ginf.smarthealthcare.orchestrator.workflow.Ports.PriseEnCharge;

/**
 * Trace des workflows dans la base de l'orchestrateur (P5.3, D54 ; migration V1__trace_workflows.sql) : écrite par
 * {@link tn.ginf.smarthealthcare.orchestrator.workflow.WorkflowAlerte}, lue par {@link WorkflowsController}.
 */
@Repository
public class DepotTraces implements Ports.Traces {

    /** Schémas WorkflowResume et Workflow du contrat contracts/orchestrateur/workflows-openapi.yaml. */
    public record WorkflowResume(String id, String alerteId, String patientId, String niveau, String message,
                                 OffsetDateTime debut, OffsetDateTime fin, Long dureeMs, String etat, boolean aRegulariser,
                                 String rendezVousId, String interventionId, String ficheId, String erreur) {}

    public record Etape(int ordre, String nom, String service, OffsetDateTime debut, long dureeMs, int tentatives,
                        String statut, String detail) {}

    public record Workflow(String id, String alerteId, String patientId, String niveau, String message,
                           OffsetDateTime debut, OffsetDateTime fin, Long dureeMs, String etat, boolean aRegulariser,
                           String rendezVousId, String interventionId, String ficheId, String erreur,
                           List<Etape> etapes) {}

    private static final String COLONNES = "id, alerte_id, patient_id, niveau, message, debut, fin, etat, a_regulariser, "
            + "rendez_vous_id, intervention_id, fiche_id, erreur";

    private final JdbcTemplate jdbc;
    private final ZoneId fuseau;

    public DepotTraces(JdbcTemplate jdbc, @Value("${orchestrateur.fuseau}") String fuseau) {
        this.jdbc = jdbc;
        this.fuseau = ZoneId.of(fuseau);
    }

    // ------------------------------------------------------------------ écriture (port Traces)

    @Override
    public long demarrer(AlerteRecue a, OffsetDateTime debut) {
        String message = a.message().length() <= 300 ? a.message() : a.message().substring(0, 300);
        return jdbc.queryForObject("INSERT INTO workflow (alerte_id, patient_id, niveau, message, debut, etat) "
                        + "VALUES (?, ?, ?, ?, ?, 'EN_COURS') RETURNING id", Long.class,
                a.id(), a.patientId(), a.niveau(), message, debut);
    }

    @Override
    public void etape(long workflowId, EtapeTrace e) {
        jdbc.update("INSERT INTO workflow_etape (workflow_id, ordre, nom, service, debut, duree_ms, tentatives, statut, detail) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                workflowId, e.ordre(), e.nom(), e.service(), e.debut(), e.dureeMs(), e.tentatives(), e.statut(), e.detail());
    }

    @Override
    public void terminer(long workflowId, PriseEnCharge r, String erreur, OffsetDateTime fin) {
        jdbc.update("UPDATE workflow SET fin = ?, etat = ?, a_regulariser = ?, rendez_vous_id = ?, intervention_id = ?, "
                        + "fiche_id = ?, erreur = ? WHERE id = ?",
                fin, r.etat(), r.aRegulariser(), r.rendezVousId(), r.interventionId(), r.ficheId(), erreur,
                workflowId);
    }

    // ------------------------------------------------------------------ lecture (GET /orchestrator/workflows)

    public List<WorkflowResume> lister(String patientId, String etat, int limite) {
        StringBuilder sql = new StringBuilder("SELECT " + COLONNES + " FROM workflow WHERE 1 = 1");
        List<Object> parametres = new ArrayList<>();
        if (patientId != null) {
            sql.append(" AND patient_id = ?");
            parametres.add(patientId);
        }
        if (etat != null) {
            sql.append(" AND etat = ?");
            parametres.add(etat);
        }
        sql.append(" ORDER BY id DESC LIMIT ?");
        parametres.add(limite);
        return jdbc.query(sql.toString(), (rs, n) -> resume(rs), parametres.toArray());
    }

    public Optional<Workflow> obtenir(long id) {
        List<WorkflowResume> trouve = jdbc.query("SELECT " + COLONNES + " FROM workflow WHERE id = ?", (rs, n) -> resume(rs), id);
        if (trouve.isEmpty()) {
            return Optional.empty();
        }
        WorkflowResume w = trouve.get(0);
        List<Etape> etapes = jdbc.query("SELECT ordre, nom, service, debut, duree_ms, tentatives, statut, detail "
                        + "FROM workflow_etape WHERE workflow_id = ? ORDER BY ordre",
                (rs, n) -> new Etape(rs.getInt("ordre"), rs.getString("nom"), rs.getString("service"), date(rs, "debut"),
                        rs.getLong("duree_ms"), rs.getInt("tentatives"), rs.getString("statut"), rs.getString("detail")), id);
        return Optional.of(new Workflow(w.id(), w.alerteId(), w.patientId(), w.niveau(), w.message(), w.debut(), w.fin(),
                w.dureeMs(), w.etat(), w.aRegulariser(), w.rendezVousId(), w.interventionId(), w.ficheId(), w.erreur(), etapes));
    }

    private WorkflowResume resume(ResultSet rs) throws SQLException {
        Timestamp debutPrecis = rs.getTimestamp("debut");
        Timestamp finPrecise = rs.getTimestamp("fin");
        Long dureeMs = finPrecise == null ? null : finPrecise.getTime() - debutPrecis.getTime();   // avant l'arrondi
        return new WorkflowResume("WF-%06d".formatted(rs.getLong("id")), rs.getString("alerte_id"), rs.getString("patient_id"),
                rs.getString("niveau"), rs.getString("message"), date(rs, "debut"), date(rs, "fin"), dureeMs, rs.getString("etat"),
                rs.getBoolean("a_regulariser"), rs.getString("rendez_vous_id"), rs.getString("intervention_id"),
                rs.getString("fiche_id"), rs.getString("erreur"));
    }

    /** Dates du contrat : ISO 8601 avec décalage, à la seconde, heure de Tunis. */
    private OffsetDateTime date(ResultSet rs, String colonne) throws SQLException {
        Timestamp t = rs.getTimestamp(colonne);
        return t == null ? null : t.toInstant().atZone(fuseau).toOffsetDateTime().truncatedTo(ChronoUnit.SECONDS);
    }
}
