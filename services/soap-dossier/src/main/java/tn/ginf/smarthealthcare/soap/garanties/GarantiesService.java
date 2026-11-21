package tn.ginf.smarthealthcare.soap.garanties;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import javax.xml.datatype.XMLGregorianCalendar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.ginf.smarthealthcare.soap.droits.DroitsService;
import tn.ginf.smarthealthcare.soap.droits.DroitsService.Droits;
import tn.ginf.smarthealthcare.soap.facturation.FacturationService;
import tn.ginf.smarthealthcare.soap.generated.Avantage;
import tn.ginf.smarthealthcare.soap.generated.ComparerGarantiesResponse;
import tn.ginf.smarthealthcare.soap.generated.ErreurMetierFault;
import tn.ginf.smarthealthcare.soap.generated.Garantie;
import tn.ginf.smarthealthcare.soap.generated.PriseEnCharge;

/**
 * Comparaison des garanties entre deux prises en charge (P2.SOAP.3, D16) : pour chacune, droits CNAM à la date,
 * taux applicable (0 si les droits ne sont pas ouverts), montant de l'acte et montant remboursé ;
 * puis écart = remboursé(A) − remboursé(B) et prise en charge la plus avantageuse (remboursement le plus élevé).
 */
@Service
public class GarantiesService {

    private static final Logger log = LoggerFactory.getLogger(GarantiesService.class);

    private final DroitsService droits;
    private final FacturationService facturation;

    public GarantiesService(DroitsService droits, FacturationService facturation) {
        this.droits = droits;
        this.facturation = facturation;
    }

    public ComparerGarantiesResponse comparer(PriseEnCharge a, PriseEnCharge b) throws ErreurMetierFault {
        Garantie ga = garantie(a);
        Garantie gb = garantie(b);
        BigDecimal ecart = ga.getMontantRembourse().subtract(gb.getMontantRembourse()).setScale(3, RoundingMode.HALF_UP);

        ComparerGarantiesResponse r = new ComparerGarantiesResponse();
        r.setGarantieA(ga);
        r.setGarantieB(gb);
        r.setEcartMontantRembourse(ecart);
        r.setPlusAvantageuse(ecart.signum() > 0 ? Avantage.A : ecart.signum() < 0 ? Avantage.B : Avantage.EGALE);
        log.info("comparerGaranties A={} ({} %, {} TND) B={} ({} %, {} TND) écart={} -> {}",
                a.getIdentifiantPatient(), ga.getTauxRemboursement(), ga.getMontantRembourse(),
                b.getIdentifiantPatient(), gb.getTauxRemboursement(), gb.getMontantRembourse(), ecart, r.getPlusAvantageuse());
        return r;
    }

    private Garantie garantie(PriseEnCharge p) throws ErreurMetierFault {
        Droits d = droits.evaluer(p.getIdentifiantPatient(), versLocalDate(p.getDatePrestation()));
        BigDecimal montant = facturation.montantActe(p.getLettreCle(), p.getCoefficient());
        Garantie g = new Garantie();
        g.setPriseEnCharge(p);
        g.setStatut(d.statut());
        g.setTauxRemboursement(d.tauxApplicable());
        g.setMontantActe(montant);
        g.setMontantRembourse(FacturationService.rembourse(montant, d.tauxApplicable()));
        return g;
    }

    private static LocalDate versLocalDate(XMLGregorianCalendar d) {
        return LocalDate.of(d.getYear(), d.getMonth(), d.getDay());
    }
}
