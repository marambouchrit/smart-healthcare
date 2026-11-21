package tn.ginf.smarthealthcare.soap;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SoapDossierApplication {

    public static void main(String[] args) {
        SpringApplication.run(SoapDossierApplication.class, args);
    }

    /** Horloge injectable (date d'émission des feuilles) ; les tests la remplacent. */
    @Bean
    Clock horloge(@Value("${dossier.fuseau}") String fuseau) {
        return Clock.system(ZoneId.of(fuseau));
    }
}
