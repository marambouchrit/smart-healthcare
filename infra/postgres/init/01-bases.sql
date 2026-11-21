-- Exécuté une seule fois, à la création du volume (docker-entrypoint-initdb.d).
-- Une base et un utilisateur par service (D8) ; aucun service n'accède à la base d'un autre.
-- Mots de passe de DÉVELOPPEMENT (D35).

CREATE USER soap_dossier WITH PASSWORD 'soap_dossier_dev';
CREATE DATABASE soap_dossier OWNER soap_dossier;

CREATE USER rest_portail WITH PASSWORD 'rest_portail_dev';
CREATE DATABASE rest_portail OWNER rest_portail;

CREATE USER graphql_dashboard WITH PASSWORD 'graphql_dashboard_dev';
CREATE DATABASE graphql_dashboard OWNER graphql_dashboard;

CREATE USER grpc_telemetrie WITH PASSWORD 'grpc_telemetrie_dev';
CREATE DATABASE grpc_telemetrie OWNER grpc_telemetrie;

-- Empêche chaque utilisateur de se connecter aux bases des autres services
REVOKE CONNECT ON DATABASE soap_dossier FROM PUBLIC;
REVOKE CONNECT ON DATABASE rest_portail FROM PUBLIC;
REVOKE CONNECT ON DATABASE graphql_dashboard FROM PUBLIC;
REVOKE CONNECT ON DATABASE grpc_telemetrie FROM PUBLIC;
