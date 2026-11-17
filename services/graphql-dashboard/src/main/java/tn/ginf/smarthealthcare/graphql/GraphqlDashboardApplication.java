package tn.ginf.smarthealthcare.graphql;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GraphqlDashboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(GraphqlDashboardApplication.class, args);
    }

    @Bean
    Clock horloge(@Value("${dashboard.fuseau}") String fuseau) {
        return Clock.system(ZoneId.of(fuseau));
    }
}
