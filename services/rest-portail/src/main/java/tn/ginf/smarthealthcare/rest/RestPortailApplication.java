package tn.ginf.smarthealthcare.rest;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
// Les dépôts sont des interfaces imbriquées dans persistance.Depots
@EnableJpaRepositories(basePackages = "tn.ginf.smarthealthcare.rest.persistance", considerNestedRepositories = true)
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
