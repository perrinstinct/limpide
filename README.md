# Limpide

> La banque sans zone d'ombre.

Limpide est une **banque fictive**, sans aucun argent réel, construite comme projet portfolio. Sa promesse : chaque opération peut être ouverte pour voir les coulisses, c'est-à-dire les écritures comptables, les contrôles passés et les messages échangés avec les autres banques.

Projet en construction : voir le [plan de développement](docs/PLAN.md).

## Stack

- **Backend :** Java 25, Spring Boot 4.1, Spring Modulith, PostgreSQL 18, Flyway
- **Frontend :** Angular 22
- **Infra :** Docker Compose, Caddy, GitHub Actions, VPS

## Lancer en local

Prérequis : Java 25 ou plus, Node 24 ou plus, Docker.

```bash
cd backend && ./mvnw spring-boot:run
```

PostgreSQL démarre automatiquement grâce au support Docker Compose de Spring Boot.

```bash
cd frontend && npm install && npm start
```

L'appli est alors sur http://localhost:4200.

## Documentation

- [Plan de développement](docs/PLAN.md)
- [Décisions d'architecture (ADR)](docs/adr)
- [Déploiement](docs/DEPLOIEMENT.md)
