# ADR-0002 : un grand livre en ajout seul, sans event sourcing complet

- **Statut :** accepté
- **Date :** 2026-10-25

## Contexte

Une banque ne doit jamais créer ni perdre d'argent, et doit pouvoir justifier chaque mouvement. L'historique doit donc être complet et inaltérable. L'event sourcing est souvent cité pour ce genre de besoin.

## Décision

Les mouvements d'argent sont des écritures en partie double stockées dans PostgreSQL, en ajout seul :

- chaque transaction comporte au moins deux écritures dont la somme fait zéro ;
- un trigger interdit `UPDATE` et `DELETE` sur les écritures ; une erreur se corrige par une contre-passation ;
- les écritures sont la source de vérité ; le solde stocké sur le compte n'est qu'un cache, mis à jour dans la même transaction et sous verrou.

Pas de framework d'event sourcing. Les événements Spring Modulith servent à la communication entre modules, pas de source de vérité.

## Alternatives écartées

- **Event sourcing complet** (Axon, EventStoreDB) : il apporte des projections à maintenir, le versionnement des événements et des lectures à cohérence éventuelle. Son principal bénéfice ici, un historique immuable, est déjà obtenu : un grand livre en ajout seul est en soi un journal d'événements comptables.
- **Solde seul, modifié par `UPDATE`** : l'historique est perdu et rien ne peut être audité.

## Conséquences

- L'invariant se vérifie par une simple requête : la somme de toutes les écritures vaut zéro.
- Le modèle est celui que tout banquier connaît, ce qui facilite les échanges avec un métier bancaire.
- La lecture du solde reste rapide grâce au cache.
- Deux représentations coexistent (écritures et solde en cache) ; un contrôle de santé compare les deux et signale tout écart.
