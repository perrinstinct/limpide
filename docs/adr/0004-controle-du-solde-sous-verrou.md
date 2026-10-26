# ADR-0004 : le grand livre contrôle les soldes lui-même, sous verrou pessimiste

- **Statut :** accepté
- **Date :** 2026-10-26

## Contexte

Le test de charge de la démo lance des centaines de virements simultanés depuis un même compte. Aucun ne doit faire passer un compte sous son plancher : 0 €, ou un montant négatif en cas de découvert autorisé.

Si le module `payments` lisait le solde, le contrôlait, puis demandait au grand livre de débiter, deux virements simultanés pourraient lire le même solde, passer tous les deux le contrôle et débiter deux fois.

## Décision

- **Deux sortes de comptes.** Le grand livre possède des comptes comptables : un solde, un plancher, rien d'autre. Les comptes clients (IBAN, titulaire, produit) appartiendront au module `accounts` et pointeront vers un compte comptable.
- **Montants signés.** Un crédit est positif, un débit négatif ; l'équilibre d'une transaction s'écrit « somme des écritures = 0 ».
- **Le plancher est contrôlé par le grand livre**, dans la même transaction que l'écriture : verrouillage des comptes (`SELECT … FOR UPDATE`), calcul des nouveaux soldes, refus en bloc si l'un passe sous son plancher, puis écriture. Une contrainte `CHECK` en base double ce contrôle.
- **Verrous pris dans l'ordre des identifiants**, pour que deux virements croisés (A vers B, B vers A) ne puissent pas s'attendre mutuellement.
- **Une contre-passation vérifie d'abord, sous verrou, que la transaction n'a pas déjà été contre-passée**, avant tout contrôle de solde : le motif de refus doit être le bon. La contrainte d'unicité sur `reverses_id` reste le filet de sécurité.
- **Isolation des bacs à sable par clés étrangères composites** : une écriture ne peut viser qu'un compte et une transaction de son propre bac à sable.

## Alternatives écartées

- **Contrôle du solde dans `payments`** : vulnérable aux accès concurrents décrits plus haut, sauf à dupliquer le verrouillage hors du grand livre.
- **Verrou optimiste (`@Version`)** : efficace quand les conflits sont rares. Sur un compte très sollicité, la plupart des tentatives échoueraient et devraient être relancées.
- **Isolation `SERIALIZABLE`** : correcte, mais elle remplace l'attente par des échecs à relancer, avec le même inconvénient que le verrou optimiste.

## Conséquences

- La règle « jamais sous le plancher » est garantie par construction, quel que soit le module appelant.
- Les virements sur un même compte sont sérialisés : le débit d'un compte très sollicité est limité par la durée d'une transaction, ce qui reste largement suffisant pour une banque de détail.
- Une contre-passation peut être refusée si le compte crédité a dépensé l'argent entre-temps. C'est le comportement attendu d'un plancher ; le back-office devra le traiter (phase 5).
