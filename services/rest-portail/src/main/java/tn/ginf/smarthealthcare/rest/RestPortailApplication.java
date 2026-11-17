package tn.ginf.smarthealthcare.rest;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class RestPortailApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestPortailApplication.class, args);
    }

    /** Horloge injectable : les tests la remplacent pour fixer « maintenant ». */
    @Bean
    Clock horloge(@Value("${portail.fuseau}") String fuseau) {
        return Clock.system(ZoneId.of(fuseau));
    }
}
