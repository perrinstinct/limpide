# Plan de développement

Limpide est une banque fictive dont la promesse est la transparence : chaque opération peut être ouverte pour « voir les coulisses ». Ce plan découpe le projet en phases courtes, chacune terminée par une version en ligne et démontrable.

## Hypothèses

- Monolithe modulaire (Spring Modulith), Java 25, Spring Boot 4.1, Maven, PostgreSQL ; front Angular 22.
- Hébergement sur un VPS existant, Docker Compose et Caddy.
- Domaine reporté à la phase 6 : en attendant, l'appli est servie sur une adresse sslip.io, et toutes les URL publiques viennent de la configuration.
- Estimations en jours de travail effectif.

## Règles du jeu

- **En ligne dès la phase 0.** Chaque phase se termine par une version déployée.
- **Les concepts d'abord.** Chaque phase commence par poser les notions métier et techniques, le code vient ensuite.
- **« Terminé » veut dire :** tests verts en CI, déployé, README à jour, décisions notées en ADR.
- **Le bac à sable dès le premier jour.** Chaque table métier porte un `sandbox_id` dès sa création ([ADR-0003](adr/0003-bac-a-sable-par-visiteur.md)).

## V1 : le cœur

### Phase 0 : fondations (2 à 3 jours)

- Dépôt unique : `backend/`, `frontend/`, `infra/`, `docs/`.
- Squelette Spring Boot avec les modules Modulith vides et un test qui vérifie leurs frontières.
- Squelette Angular.
- Docker Compose en dev, Testcontainers en test, Flyway pour le schéma.
- GitHub Actions : tests, images publiées sur GHCR, déploiement sur le VPS.
- ADR 1 à 3.
- **Terminé quand :** l'adresse publique affiche la page d'accueil en HTTPS, et chaque push sur `main` redéploie.

### Phase 1 : le grand livre (4 à 5 jours)

- Tables comptes, transactions, écritures.
- Une seule porte d'entrée, `comptabiliser(transaction)`, qui refuse toute transaction déséquilibrée ou à moins de deux écritures.
- Un trigger PostgreSQL interdit la modification et la suppression des écritures ; les corrections passent par une contre-passation.
- Solde mis en cache sur le compte, mis à jour dans la même transaction, sous verrou.
- Contrôle de santé en échec si l'équilibre global est rompu.
- Tests : Testcontainers, concurrence (100 threads sur le même compte), tests par propriétés avec jqwik.
- **Terminé quand :** les tests prouvent qu'on ne peut ni créer ni perdre un centime.

### Phase 2 : comptes et virements internes (5 à 7 jours)

- Clients, comptes courants et livrets, IBAN fictifs valides (clé modulo 97, code banque inventé), bénéficiaires.
- API de virement : contrôles (solde disponible, plafond, bénéficiaire), parcours saisi → contrôlé → exécuté ou rejeté, chaque contrôle enregistré.
- Idempotence par l'en-tête `Idempotency-Key`.
- Piste d'audit avec `@Audited`, conservée même si la transaction échoue (`REQUIRES_NEW`).
- Événements entre modules, erreurs `ProblemDetail`, documentation OpenAPI.
- **Terminé quand :** renvoyer un virement ne débite pas deux fois, et 200 virements simultanés ne dépassent jamais le découvert autorisé.

### Phase 3 : sécurité (3 à 4 jours)

- Spring Authorization Server : la page de connexion est un sélecteur de persona, avec un vrai flux OpenID Connect (ADR 4).
- Le jeton porte le persona et l'identifiant du bac à sable.
- Rôles client, conseiller, superviseur ; `@PreAuthorize`.
- Authentification forte simulée par code à usage unique.
- **Terminé quand :** des tests prouvent qu'Alice ne peut ni voir ni toucher les comptes de Bob, ni ceux d'un autre bac à sable.

### Phase 4 : l'appli client et l'identité Limpide (6 à 8 jours)

- Logo, palette, typographies, composants de base.
- Client d'API généré depuis l'OpenAPI du backend.
- Écrans : connexion par persona, comptes, historique, virement, bénéficiaires.
- Textes dans le ton Limpide, version mobile soignée.
- **Terminé quand :** un visiteur fait un virement de bout en bout depuis son téléphone.

### Phase 5 : les coulisses (4 à 5 jours)

- Bouton « Voir les coulisses » sur chaque opération : contrôles, écritures, audit.
- Grand livre en direct (SSE) avec l'indicateur d'équilibre.
- Bouton de test de charge : 200 virements simultanés et leur bilan.
- Espace conseiller ; métriques et traces (Micrometer, OpenTelemetry).
- **Terminé quand :** un recruteur comprend en 30 secondes ce qui se passe sous le capot.

### Phase 6 : la démo publique (4 à 6 jours)

- Bac à sable créé à la première visite, avec six mois d'historique réaliste, supprimé après 24 h.
- Limite de requêtes (Bucket4j), bandeau « Banque fictive, aucun argent réel ».
- Page d'accueil, page « Recruteurs : l'architecture », README avec GIF.
- Vérification du nom (INPI), domaine définitif, surveillance de disponibilité.
- **Terminé quand :** le lien peut être envoyé à un prospect sans explication.

## V2 : les échanges avec l'extérieur

### Phase 7 : virements SEPA et ISO 20022 (8 à 12 jours)

- Starter `limpide-iso20022` : `pain.001`, `pacs.008`, `pacs.002`, `pacs.004`, validés contre les XSD officiels.
- Chambre de compensation et banque partenaire en applis séparées, reliées par JMS (ActiveMQ Artemis).
- Outbox, virement instantané en 10 secondes, vérification du bénéficiaire, rejets aléatoires réglables.

### Phase 8 : la fin de journée (5 à 8 jours)

- Spring Batch : intérêts, frais, relevés `camt.053` et PDF, avec reprise après plantage.
- Rapprochement avec la chambre de compensation.
- Virements programmés (`@Scheduled` et ShedLock).

## V3 : les bonus, au choix

Paiement carte, règles anti-fraude, principe des quatre yeux, mode panne.
