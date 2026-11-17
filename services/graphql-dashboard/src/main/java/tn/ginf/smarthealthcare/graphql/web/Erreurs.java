package tn.ginf.smarthealthcare.graphql.web;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

/** Traduit les erreurs métier en erreurs GraphQL typées (NOT_FOUND, BAD_REQUEST) au lieu de INTERNAL_ERROR. */
@Component
public class Erreurs extends DataFetcherExceptionResolverAdapter {

    public static class NonTrouve extends RuntimeException {
        public NonTrouve(String message) { super(message); }
    }

    public static class Invalide extends RuntimeException {
        public Invalide(String message) { super(message); }
    }

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        ErrorType type = ex instanceof NonTrouve ? ErrorType.NOT_FOUND
                : ex instanceof Invalide ? ErrorType.BAD_REQUEST
                : null;
        if (type == null) {
            return null; // traitement par défaut de Spring
        }
        return GraphqlErrorBuilder.newError(env).errorType(type).message(ex.getMessage()).build();
    }
}
