package tn.ginf.smarthealthcare.soap.persistance;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "lettre_cle")
public class LettreCleEntite {
    @Id
    private String code;
    private String libelle;
    private BigDecimal valeurUnitaire;

    protected LettreCleEntite() {}

    public LettreCleEntite(String code, String libelle, BigDecimal valeurUnitaire) {
        this.code = code;
        this.libelle = libelle;
        this.valeurUnitaire = valeurUnitaire;
    }

    public String getCode() { return code; }
    public BigDecimal getValeurUnitaire() { return valeurUnitaire; }
}
