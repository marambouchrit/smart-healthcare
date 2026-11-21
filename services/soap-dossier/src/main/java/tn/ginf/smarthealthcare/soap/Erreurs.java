package tn.ginf.smarthealthcare.soap;

import tn.ginf.smarthealthcare.soap.generated.CodeErreur;
import tn.ginf.smarthealthcare.soap.generated.ErreurMetier;
import tn.ginf.smarthealthcare.soap.generated.ErreurMetierFault;

/** Fabrique des SOAP Faults métier (élément erreurMetier du contrat). */
public final class Erreurs {

    private Erreurs() {}

    public static ErreurMetierFault faute(CodeErreur code, String message) {
        ErreurMetier erreur = new ErreurMetier();
        erreur.setCode(code);
        erreur.setMessage(message);
        return new ErreurMetierFault(message, erreur);
    }
}
