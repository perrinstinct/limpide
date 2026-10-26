# Déploiement

À chaque push sur `main`, la CI joue les tests, publie les images `limpide-api` et `limpide-web` sur le registre de GitHub (GHCR), étiquetées avec l'identifiant du commit, puis demande au VPS de les récupérer et de redémarrer.

## Préparer le VPS (une seule fois)

1. Installer Docker et le plugin Compose.
2. Créer un utilisateur dédié au déploiement, membre du groupe `docker` :

   ```bash
   sudo useradd --create-home --groups docker deploy
   ```

3. Créer le dossier de l'appli et son fichier `.env` à partir de [`infra/.env.example`](../infra/.env.example) :

   ```bash
   sudo install -d -o deploy -g deploy /opt/limpide
   ```

   Dans `.env`, `PUBLIC_HOST` vaut l'IP du VPS avec des tirets suivie de `.sslip.io` (par exemple `203-0-113-10.sslip.io`), et `POSTGRES_PASSWORD` un mot de passe long et aléatoire.

4. Ouvrir les ports 80 et 443 (TCP, et UDP pour le 443) dans le pare-feu. Aucun autre service ne doit déjà les occuper.

## Relier GitHub au VPS (une seule fois)

1. Générer une paire de clés réservée à la CI, sur ta machine :

   ```bash
   ssh-keygen -t ed25519 -N '' -C limpide-ci -f limpide-ci
   ```

2. Ajouter `limpide-ci.pub` au fichier `~/.ssh/authorized_keys` de l'utilisateur `deploy` sur le VPS.
3. Dans le dépôt GitHub, *Settings → Secrets and variables → Actions*, créer les secrets :

   | Secret | Valeur |
   |---|---|
   | `VPS_HOST` | IP du VPS |
   | `VPS_USER` | `deploy` |
   | `VPS_SSH_KEY` | contenu du fichier `limpide-ci` (clé privée) |
   | `VPS_KNOWN_HOSTS` | sortie de `ssh-keyscan -H <IP du VPS>`, en vérifiant l'empreinte |

4. Créer la variable de dépôt `DEPLOY_ENABLED` avec la valeur `true`. Tant qu'elle n'existe pas, la CI teste sans déployer.

## Revenir à une version précédente

Chaque image porte l'identifiant de son commit. Sur le VPS :

```bash
cd /opt/limpide && IMAGE_TAG=<identifiant du commit> docker compose up -d
```

Le registre étant privé par défaut, se connecter d'abord avec `docker login ghcr.io` et un jeton personnel ayant le droit `read:packages`, ou rendre les images publiques depuis la page *Packages* du dépôt.
