package tn.ginf.smarthealthcare.graphql.web;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.Function;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * Scalaires du contrat : Date (AAAA-MM-JJ) ↔ LocalDate, DateTime (ISO 8601 avec fuseau) ↔ OffsetDateTime.
 * Implémentés ici pour éviter une dépendance supplémentaire.
 */
@Configuration
public class Scalaires {

    @Bean
    RuntimeWiringConfigurer scalairesDuContrat() {
        return wiring -> wiring
                .scalar(scalaire("Date", "Date ISO 8601 (AAAA-MM-JJ)", LocalDate.class, LocalDate::parse, LocalDate::toString))
                .scalar(scalaire("DateTime", "Date et heure ISO 8601 avec fuseau", OffsetDateTime.class, OffsetDateTime::parse,
                        d -> d.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))); // secondes toujours écrites
    }

    @SuppressWarnings("deprecation") // méthodes historiques de Coercing, toujours supportées par graphql-java
    private static <T> GraphQLScalarType scalaire(String nom, String description, Class<T> type,
                                                Function<String, T> lecture, Function<T, String> ecriture) {
        return GraphQLScalarType.newScalar().name(nom).description(description).coercing(new Coercing<T, String>() {
            @Override
            public String serialize(Object valeur) {
                if (type.isInstance(valeur)) {
                    return ecriture.apply(type.cast(valeur));
                }
                throw new CoercingSerializeException(nom + " attendu, reçu : " + valeur);
            }

            @Override
            public T parseValue(Object entree) {
                try {
                    return lecture.apply(entree.toString());
                } catch (DateTimeParseException e) {
                    throw new CoercingParseValueException(nom + " invalide : " + entree);
                }
            }

            @Override
            public T parseLiteral(Object litteral) {
                if (litteral instanceof StringValue s) {
                    try {
                        return lecture.apply(s.getValue());
                    } catch (DateTimeParseException e) {
                        throw new CoercingParseLiteralException(nom + " invalide : " + s.getValue());
                    }
                }
                throw new CoercingParseLiteralException(nom + " doit être une chaîne");
            }
        }).build();
    }
}
