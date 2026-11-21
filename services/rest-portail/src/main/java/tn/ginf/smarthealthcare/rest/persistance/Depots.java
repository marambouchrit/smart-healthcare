package tn.ginf.smarthealthcare.rest.persistance;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tn.ginf.smarthealthcare.rest.persistance.Entites.CreneauEntite;
import tn.ginf.smarthealthcare.rest.persistance.Entites.MedecinEntite;
import tn.ginf.smarthealthcare.rest.persistance.Entites.PatientEntite;
import tn.ginf.smarthealthcare.rest.persistance.Entites.RendezVousEntite;
import tn.ginf.smarthealthcare.rest.persistance.Entites.TraitementEntite;

/** Dépôts Spring Data JPA du portail. */
public final class Depots {

    private Depots() {}

    public interface Medecins extends JpaRepository<MedecinEntite, String> {}

    public interface Patients extends JpaRepository<PatientEntite, String> {}

    public interface Traitements extends JpaRepository<TraitementEntite, String> {}

    public interface Creneaux extends JpaRepository<CreneauEntite, Long> {
        boolean existsByMedecinIdAndDebut(String medecinId, OffsetDateTime debut);

        List<CreneauEntite> findAllByOrderByDebutAscMedecinIdAsc();

        List<CreneauEntite> findByDebutGreaterThanEqualAndDebutLessThanOrderByDebutAscMedecinIdAsc(OffsetDateTime de, OffsetDateTime a);
    }

    public interface RendezVous extends JpaRepository<RendezVousEntite, Long> {
        /** Créneaux portant un rendez-vous non annulé (donc indisponibles). */
        @Query("select r.creneauId from RendezVousEntite r where r.statut <> 'ANNULE'")
        Set<Long> creneauxReserves();

        boolean existsByCreneauIdAndStatutNot(Long creneauId, String statut);

        List<RendezVousEntite> findAllByOrderByIdAsc();
    }
}
