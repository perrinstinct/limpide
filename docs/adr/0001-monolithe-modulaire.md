# ADR-0001 : un monolithe modulaire avec Spring Modulith

- **Statut :** accepté
- **Date :** 2026-10-25

## Contexte

Limpide couvre plusieurs domaines qui doivent rester indépendants : grand livre, comptes, paiements, audit, back-office, bacs à sable. Le projet est développé par une seule personne et hébergé sur un seul VPS pour une démo publique. Les microservices sont fréquents dans les missions bancaires, ce qui pousse à les montrer.

Le cœur du métier, le grand livre, exige qu'un virement interne débite un compte et en crédite un autre de façon atomique.

## Décision

Un seul déployable Spring Boot, découpé en modules Spring Modulith. Un module est un sous-package direct de `fr.limpide`.

- Un module n'expose que les types de son package racine ; ses sous-packages sont internes.
- Les modules communiquent par ces types publics ou par événements.
- Un test (`ModularityTests`) vérifie ces règles et fait échouer le build en cas de violation.

Exceptions prévues : le serveur d'autorisation (phase 3), la banque partenaire et la chambre de compensation (V2) seront des applications séparées, car elles représentent des systèmes réellement distincts.

## Alternatives écartées

- **Microservices :** un virement interne deviendrait une transaction distribuée (saga, compensation) là où une transaction PostgreSQL suffit. S'y ajoutent plusieurs déploiements, du réseau entre services et un hébergement plus cher, sans bénéfice pour une démo.
- **Monolithe en couches classique** (`controller`, `service`, `repository`) : rien n'empêche un domaine d'appeler les entrailles d'un autre, et le couplage s'installe sans qu'on le voie.

## Conséquences

- Une seule transaction de base de données couvre un virement interne de bout en bout.
- Hébergement léger : une appli, une base.
- Les frontières vérifiées faciliteront l'extraction d'un module en service si le besoin apparaît.
- Il faut de la discipline sur les frontières ; le test la rend automatique.
- Pas de montée en charge indépendante par module, ce qui n'est pas un besoin ici.
