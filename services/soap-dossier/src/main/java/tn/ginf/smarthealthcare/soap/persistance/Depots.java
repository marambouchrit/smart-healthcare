package tn.ginf.smarthealthcare.soap.persistance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Dépôts Spring Data JPA du service SOAP. */
public final class Depots {

    private Depots() {}

    public interface Patients extends JpaRepository<PatientEntite, String> {}

    public interface Medecins extends JpaRepository<MedecinEntite, String> {}

    public interface Couvertures extends JpaRepository<CouvertureEntite, String> {}

    public interface LettresCles extends JpaRepository<LettreCleEntite, String> {}

    public interface Traitements extends JpaRepository<TraitementEntite, String> {
        List<TraitementEntite> findByPatientIdOrderByDateDebutAscIdAsc(String patientId);
    }

    public interface Feuilles extends JpaRepository<FeuilleEntite, Long> {
        List<FeuilleEntite> findByPatientIdOrderByIdAsc(String patientId);
    }
}
