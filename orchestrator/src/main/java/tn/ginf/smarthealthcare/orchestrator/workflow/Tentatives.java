package tn.ginf.smarthealthcare.orchestrator.workflow;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Nouveaux essais « sûrs » (P5.3, D54) : jusqu'à {@code maximum} appels, avec une attente doublée à chaque fois
 * (0,5 s puis 1 s par défaut), uniquement sur {@link ServiceIndisponibleException}.
 * <ul>
 *   <li>lecture ou opération idempotente : nouvel essai sur toute panne technique ;</li>
 *   <li>opération qui crée quelque chose (rendez-vous, ambulance, fiche) : nouvel essai seulement si la requête
 *       n'a pas pu partir, pour ne jamais créer de doublon quand seule la réponse s'est perdue.</li>
 * </ul>
 * Une erreur métier (SOAP Fault, 4xx, erreur GraphQL) n'est jamais réessayée.
 */
public class Tentatives {

    /** Attente entre deux essais ; remplacée dans les tests pour ne pas dormir. */
    @FunctionalInterface
    public interface Attente {
        void attendre(Duration duree) throws InterruptedException;
    }

    /** Résultat d'une étape : la valeur ou l'erreur finale, et le nombre d'appels effectués. */
    public record Issue<T>(T valeur, RuntimeException erreur, int essais) {
        public boolean reussie() {
            return erreur == null;
        }
    }

    private final int maximum;
    private final Duration attenteInitiale;
    private final Attente attente;

    public Tentatives(int maximum, Duration attenteInitiale, Attente attente) {
        if (maximum < 1) {
            throw new IllegalArgumentException("au moins un essai");
        }
        this.maximum = maximum;
        this.attenteInitiale = attenteInitiale;
        this.attente = attente;
    }

    public static Tentatives sansAttente(int maximum) {
        return new Tentatives(maximum, Duration.ZERO, d -> {});
    }

    public <T> Issue<T> executer(boolean creeUneRessource, Supplier<T> appel) {
        Duration pause = attenteInitiale;
        for (int essai = 1; ; essai++) {
            try {
                return new Issue<>(appel.get(), null, essai);
            } catch (ServiceIndisponibleException e) {
                boolean sur = !creeUneRessource || !e.requeteEnvoyee();
                if (!sur || essai >= maximum) {
                    return new Issue<>(null, e, essai);
                }
                try {
                    attente.attendre(pause);
                } catch (InterruptedException interruption) {
                    Thread.currentThread().interrupt();
                    return new Issue<>(null, e, essai);
                }
                pause = pause.multipliedBy(2);
            } catch (RuntimeException e) {
                return new Issue<>(null, e, essai);   // erreur métier : jamais réessayée
            }
        }
    }
}
