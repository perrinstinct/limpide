# ADR-0003 : un bac à sable par visiteur

- **Statut :** accepté
- **Date :** 2026-10-25

## Contexte

La démo est publique et sans inscription. Si tous les visiteurs partagent les mêmes comptes, chacun voit les virements des autres, les données se dégradent au fil des visites, et les libellés de virement, saisis librement, deviennent un contenu public à modérer.

## Décision

Chaque visiteur reçoit son propre bac à sable : un ensemble isolé de clients, de comptes et d'historique, créé à sa première visite et supprimé au bout de 24 heures.

- La table `sandbox` est la racine de l'isolation ; toutes les tables métier portent une colonne `sandbox_id` dès leur création.
- L'identifiant est un UUID aléatoire, impossible à deviner. À partir de la phase 3, il voyage dans le jeton d'authentification.
- L'équilibre du grand livre se vérifie par bac à sable.

## Alternatives écartées

- **Base partagée remise à zéro chaque nuit :** les visiteurs se gênent entre eux et les libellés libres sont visibles de tous.
- **Un schéma PostgreSQL par visiteur :** isolation forte, mais des milliers de schémas et des migrations Flyway à rejouer sur chacun.
- **Une base par visiteur :** beaucoup trop lourd pour un VPS.

## Conséquences

- Chaque visiteur peut tout essayer, y compris le test de charge, sans gêner personne.
- Aucun contenu partagé, donc rien à modérer.
- Chaque requête doit filtrer par `sandbox_id` : un oubli ferait fuiter des données d'un bac à sable à l'autre. Le filtrage sera centralisé en phase 3 et couvert par des tests d'isolation.
- La purge doit être surveillée pour que la base ne grossisse pas indéfiniment.
