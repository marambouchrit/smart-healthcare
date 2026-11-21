package tn.ginf.smarthealthcare.soap;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Catalogue des SOAP Faults (P2.SOAP.4), vérifié au niveau du message échangé (HTTP + enveloppe SOAP brute),
 * c'est-à-dire ce que reçoit n'importe quel client (SoapUI, orchestrateur, autre langage).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FautesSoapTest {

    @LocalServerPort int port;

    static final String NS = "http://hopital.tn/dossier";

    static final String DROITS = "<d:verifierDroitsCNAM xmlns:d='" + NS + "'><d:identifiantPatient>%s</d:identifiantPatient>"
            + "<d:lettreCle>C</d:lettreCle><d:datePrestation>2026-11-17</d:datePrestation></d:verifierDroitsCNAM>";
    static final String FEUILLE = "<d:emettreFeuilleDeSoins xmlns:d='" + NS + "'><d:identifiantPatient>%s</d:identifiantPatient>"
            + "<d:codeVignette>%s</d:codeVignette><d:acte><d:lettreCle>C</d:lettreCle><d:coefficient>1</d:coefficient>"
            + "<d:libelle>Consultation</d:libelle><d:dateActe>2026-11-17</d:dateActe><d:identifiantMedecin>%s</d:identifiantMedecin>"
            + "</d:acte></d:emettreFeuilleDeSoins>";
    static final String HISTORIQUE = "<d:consulterHistorique xmlns:d='" + NS + "'><d:identifiantPatient>%s</d:identifiantPatient>"
            + "<d:dateDebut>%s</d:dateDebut><d:dateFin>%s</d:dateFin></d:consulterHistorique>";
    static final String PEC = "<d:%1$s><d:identifiantPatient>%2$s</d:identifiantPatient><d:lettreCle>C</d:lettreCle>"
            + "<d:coefficient>1</d:coefficient><d:datePrestation>2026-11-17</d:datePrestation></d:%1$s>";
    static final String COMPARER = "<d:comparerGaranties xmlns:d='" + NS + "'>" + PEC + PEC.replace("%1$s", "%3$s").replace("%2$s", "%4$s")
            + "</d:comparerGaranties>";

    HttpResponse<String> appeler(String operation, String corps) throws Exception {
        String enveloppe = "<soapenv:Envelope xmlns:soapenv='http://schemas.xmlsoap.org/soap/envelope/'><soapenv:Body>"
                + corps + "</soapenv:Body></soapenv:Envelope>";
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ws/dossier"))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"" + NS + "/" + operation + "\"")
                .POST(HttpRequest.BodyPublishers.ofString(enveloppe)).build();
        return HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
    }

    @ParameterizedTest(name = "{0} : {2}")
    @CsvSource(delimiter = ';', value = {
        "verifierDroitsCNAM;    PATIENT_INCONNU;   patient inconnu",
        "emettreFeuilleDeSoins; PATIENT_INCONNU;   patient inconnu",
        "emettreFeuilleDeSoins; VIGNETTE_INVALIDE; vignette expirée",
        "emettreFeuilleDeSoins; VIGNETTE_INVALIDE; vignette d'un autre patient",
        "emettreFeuilleDeSoins; DONNEES_INVALIDES; médecin inconnu",
        "consulterHistorique;   PATIENT_INCONNU;   patient inconnu",
        "consulterHistorique;   DONNEES_INVALIDES; période inversée",
        "comparerGaranties;     PATIENT_INCONNU;   patient inconnu",
    })
    void fauteMetierAvecDetailErreurMetier(String operation, String code, String cas) throws Exception {
        String corps = switch (operation + "/" + cas) {
            case "verifierDroitsCNAM/patient inconnu" -> DROITS.formatted("TN-9999999");
            case "emettreFeuilleDeSoins/patient inconnu" -> FEUILLE.formatted("TN-9999999", "VIG-2026-100001", "MED-001");
            case "emettreFeuilleDeSoins/vignette expirée" -> FEUILLE.formatted("TN-0067890", "VIG-2025-100006", "MED-003");
            case "emettreFeuilleDeSoins/vignette d'un autre patient" -> FEUILLE.formatted("TN-0012345", "VIG-2026-100002", "MED-001");
            case "emettreFeuilleDeSoins/médecin inconnu" -> FEUILLE.formatted("TN-0012345", "VIG-2026-100001", "MED-999");
            case "consulterHistorique/patient inconnu" -> HISTORIQUE.formatted("TN-9999999", "2026-01-01", "2026-12-31");
            case "consulterHistorique/période inversée" -> HISTORIQUE.formatted("TN-0012345", "2026-12-31", "2026-01-01");
            case "comparerGaranties/patient inconnu" ->
                    COMPARER.formatted("priseEnChargeA", "TN-0012345", "priseEnChargeB", "TN-9999999");
            default -> throw new IllegalArgumentException(cas);
        };
        HttpResponse<String> r = appeler(operation, corps);

        assertThat(r.statusCode()).isEqualTo(500); // SOAP 1.1 : une Fault est transportée en HTTP 500
        assertThat(r.body())
                .contains("<soap:Fault>")
                .contains("<faultcode>soap:Server</faultcode>")
                .containsPattern("<detail><erreurMetier xmlns=\"" + NS + "\"><code>" + code + "</code><message>[^<]+</message>");
    }

    @ParameterizedTest(name = "message non conforme au XSD : {0}")
    @CsvSource(delimiter = ';', value = {"identifiant mal formé; TN-12", "identifiant vide; ''"})
    void messageNonConformeAuSchemaRejete(String cas, String identifiant) throws Exception {
        HttpResponse<String> r = appeler("verifierDroitsCNAM", DROITS.formatted(identifiant));
        assertThat(r.statusCode()).isEqualTo(500);
        assertThat(r.body()).contains("<faultcode>soap:Client</faultcode>").contains("cvc-");
    }
}
