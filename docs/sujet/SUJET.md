# Sujet officiel — transcription fidèle

Transcription du PDF `Projet 3éme année ING GINF.pdf` (même dossier). En cas de doute, le PDF fait foi. Ne pas modifier ce fichier.

---

**Projet 3ème année ING GINF** — Matière : Service Oriented Computing — Année universitaire : 2026-2027

**Thème : Plateforme intelligente d'Hôpital Connecté et Suivi de Santé à Domicile (Smart Healthcare)**

## I. Contexte du projet

Aujourd'hui, le secteur de la santé évolue rapidement vers la digitalisation et le suivi à distance pour améliorer la prise en charge des patients et optimiser la gestion des infrastructures hospitalières. Dans ce contexte, il devient essentiel pour les établissements de santé de pouvoir interconnecter des services très divers, souvent issus de systèmes hétérogènes. Ces services peuvent être anciens, reposant sur des technologies établies comme SOAP (systèmes de dossiers patients institutionnels ou organismes d'assurance maladie avec normes de sécurité), ou utilisant des protocoles légers et flexibles tels que REST ou GraphQL. Certains services, en particulier ceux traitant de la télémétrie médicale et des alertes urgentes en temps réel, nécessitent des communications extrêmement rapides et fiables à faible latence, ce qui justifie l'usage de protocoles spécialisés tels que gRPC.

Dans ce cadre, vous devez développer une plateforme de services interopérables pour un hôpital connecté et un réseau de suivi de santé à domicile. Cette plateforme devra intégrer au minimum quatre services distincts, chacun utilisant un protocole différent (SOAP, REST, GraphQL, gRPC), et les exposer via un client web unique. L'objectif est de permettre aux praticiens, aux patients et aux administrateurs d'accéder facilement à des informations consolidées, et d'interagir avec les services de manière simple et transparente.

### Exemple de services web que vous pouvez implémenter

**Service 1 : Dossier Patient Informatisé & Facturation Assurance (SOAP)**
Ce service simule un système institutionnel/legacy sécurisé interfacé avec autres systèmes tel que les caisses d'assurance maladie ou le système central de l'hôpital. Il assure les fonctions suivantes :
- La vérification des droits d'assurance et de la couverture médicale pour une prestation donnée.
- L'enregistrement formel des actes médicaux et l'émission des feuilles de soins certifiées.
- La consultation de l'historique médical certifié et la comparaison des garanties entre deux prises en charge.

N.B. Pour les structures de données (XML/WSDL) : essayez de s'inspirer des nomenclatures officielles (ex : identifiant unique du patient, code de la vignette CNAM, actes médicaux cotés en lettres-clés comme les cotations de la médecine libérale ou hospitalière, taux de remboursement à 80 % ou 50 %, etc.). Et proposez des opérations genre (verifierDroitsCNAM, emettreFeuilleDeSoins, consulterHistorique).

**Service 2 : Portail Patient et Prise de Rendez-vous (REST)**
L'objectif de ce service est de permettre aux patients de gérer leurs interactions courantes avec l'établissement de santé. Le service peut offrir les fonctionnalités suivantes :
- La consultation des créneaux disponibles et la prise/modification de rendez-vous médicaux (CRUD).
- La consultation de l'historique des prescriptions et des ordonnances.
- La mise à jour du profil patient et de ses coordonnées.

**Service 3 : Tableau de Bord Médical & Consolidation de Santé (GraphQL)**
Ce service offre une vue sur-mesure et flexible aux médecins, infirmiers et gestionnaires de santé. Le service peut offrir les fonctionnalités suivantes :
- L'extraction personnalisée des dossiers patients (récupération en une seule requête du profil, des dernières constantes vitales, des alertes récentes et du traitement en cours).
- Le filtrage dynamique des patients par service hospitalier, pathologie ou niveau de risque.
- L'agrégation à la demande de l'historique de soins sans surcharger la bande passante.

**Service 4 : Télémétrie Médicale & Alerte Urgence Temps Réel (gRPC)**
Service très rapide et performant dédié à la gestion des flux en temps réel provenant des objets connectés médicaux (IoT) portés par les patients à domicile (ex. capteurs cardiaques). Ici un capteur IoT virtuel n'est qu'un simple script.
- Le streaming continu de données biométriques depuis les capteurs vers la centrale de surveillance.
- La détection immédiate et l'envoi d'alertes prioritaires en cas de dépassement de seuils critiques (ex. crise cardiaque, détresse respiratoire, chute).

## II. Travail demandé

Vous êtes obligatoirement menés à implémenter au moins 4 services distincts :
- **SOAP** : données certifiées, assurances ou systèmes legacy existants.
- **REST** : service exposant des ressources CRUD (portail & rendez-vous patient).
- **GraphQL** : service permettant des requêtes personnalisées pour le tableau de bord médical.
- **gRPC** : service pour communication rapide ou streaming temps réel (alertes urgentes).

### Partie A : Orchestration et Processus Métier

En premier lieu, les services sont relativement indépendants et orchestrés via un service qui modélise un processus métier que vous proposez. Par exemple, vous implémentez un intégrateur qui centralisera les appels aux différents services décrivant un workflow.

Exemple de workflow : pour la prise en charge automatisée d'une alerte médicale à domicile :
1. Recevoir une alerte temps réel de constante biométrique critique via gRPC.
2. Consulter le dossier patient et la couverture d'assurance via SOAP pour valider la prise en charge.
3. Réserver automatiquement un créneau de consultation d'urgence ou déclencher l'envoi d'une ambulance via REST.
4. Consolider et afficher immédiatement la fiche d'intervention d'urgence enrichie sur le tableau de bord du médecin via GraphQL.

### Partie B : Architecture Microservices & Déploiement

Présenter l'application sous forme d'une architecture microservices, avec :
- Une API Gateway centralisant les accès.
- Un client Web consommant les services via la Gateway.

Utiliser des conteneurs : emballez chaque microservice dans un conteneur Docker. Cela permet de garantir que chaque service s'exécute dans un environnement cohérent. Créez un Dockerfile pour chaque microservice.

Produire la documentation technique : OpenAPI pour REST, WSDL pour SOAP, SDL pour GraphQL, .proto pour gRPC.

### Plan de travail suggéré
1. Délimitation du périmètre fonctionnel du domaine médical.
2. Définition des 4/5 services que vous allez implémenter et du rôle de chacun.
3. Concevoir et développer progressivement les services/microservices.
4. Mise en place de l'API Gateway.
5. Développement du client Web.
6. Tests & documentation.

N.B. Les étudiants peuvent ajouter des services supplémentaires, complexifier l'orchestration, ou enrichir le client Web/Mobile.
