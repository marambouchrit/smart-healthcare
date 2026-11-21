package tn.ginf.smarthealthcare.soap.persistance;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "patient")
public class PatientEntite {
    @Id
    private String id;

    protected PatientEntite() {}

    public PatientEntite(String id) { this.id = id; }

    public String getId() { return id; }
}
