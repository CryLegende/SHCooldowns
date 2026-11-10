<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHCooldowns

*Cooldowns sur des groupes de commandes, durée selon la permission, avec bypass et placeholders.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Cooldowns sur des groupes de commandes, durée selon la permission, avec bypass et placeholders.

Utilisé sur : **Opale**.

## Dépendances

- Optionnelles : `PlaceholderAPI`, `SHNetwork` (mode réseau)

## Mode réseau

Avec **SHNetwork** installé (et `reseau.actif: true`, valeur par défaut), plusieurs serveurs de jeu partagent les cooldowns :

- chaque cooldown posé est écrit en MariaDB (table `shcooldowns` : `uuid`, `id`, `expiry`), sur un fil à part ;
- au départ d'un joueur, SHNetwork attend que ces écritures soient finies ;
- à son arrivée sur l'autre serveur, ses cooldowns sont relus en base et fusionnés avec ceux de la mémoire (l'échéance la plus tardive gagne), si bien qu'un `/fix` pris sur un serveur reste bloqué sur l'autre ;
- le temps de cette relecture, les commandes soumises à un cooldown sont refusées avec le message `reseau-chargement`.

Sans SHNetwork, ou sans les clés `reseau` dans la configuration, le plugin se comporte comme avant : cooldowns locaux, fichier `cooldowns_data.yml`. Si la base est injoignable, le joueur garde les cooldowns de son serveur. La table se crée toute seule au démarrage, ou à la main avec `sql/schema.sql`.

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/shcooldowns` | `/shc` | Commandes administratives pour SHCooldowns. | `shcooldowns.admin` |

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shcooldowns.admin` | Permet d'utiliser les commandes de gestion SHCooldowns. | `op` |
| `shcooldowns.bypass.<cooldown_id>` | Permet de contourner un cooldown spécifique (utiliser l'id du config). | `false` |
| `shcooldowns.bypass.all` | Permet de contourner tous les cooldowns SHCooldowns. | `false` |

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`). Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHCooldowns/
├── <a href="src/main/resources/config.yml">config.yml</a>
└── <a href="src/main/resources/messages.yml">messages.yml</a>
</pre>

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
