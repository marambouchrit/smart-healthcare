package tn.ginf.smarthealthcare.soap.persistance;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "medecin")
public class MedecinEntite {
    @Id
    private String id;

    protected MedecinEntite() {}

    public MedecinEntite(String id) { this.id = id; }

    public String getId() { return id; }
}
