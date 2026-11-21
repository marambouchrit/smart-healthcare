package tn.ginf.smarthealthcare.soap.persistance;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "acte_medical")
public class ActeEntite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "feuille_id")
    private FeuilleEntite feuille;
    private int ordre;
    private String lettreCle;
    private BigDecimal coefficient;
    private String libelle;
    private LocalDate dateActe;
    private String medecinId;
    private BigDecimal montant;

    protected ActeEntite() {}

    public ActeEntite(String lettreCle, BigDecimal coefficient, String libelle, LocalDate dateActe, String medecinId,
                      BigDecimal montant) {
        this.lettreCle = lettreCle;
        this.coefficient = coefficient;
        this.libelle = libelle;
        this.dateActe = dateActe;
        this.medecinId = medecinId;
        this.montant = montant;
    }

    void rattacher(FeuilleEntite feuille, int ordre) {
        this.feuille = feuille;
        this.ordre = ordre;
    }

    public String getLettreCle() { return lettreCle; }
    public BigDecimal getCoefficient() { return coefficient; }
    public String getLibelle() { return libelle; }
    public LocalDate getDateActe() { return dateActe; }
    public String getMedecinId() { return medecinId; }
    public BigDecimal getMontant() { return montant; }
}
