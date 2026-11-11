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
| `/shcooldowns reload` | — | Relit `config.yml` et `messages.yml`. Un fichier illisible ne change rien ; les cooldowns en cours sont conservés. | `shcooldowns.admin.reload` |
| `/shcooldowns info <joueur>` | — | Cooldowns actifs d'un joueur (connecté, ou par UUID) et temps restant. | `shcooldowns.admin.info` |
| `/shcooldowns reset <joueur> [id\|all]` | — | Retire le cooldown d'un groupe, ou tous sans id (en mode réseau, aussi en base). | `shcooldowns.admin.reset` |

Sans argument, ou avec une sous-commande inconnue, une ligne d'usage ne liste que les sous-commandes permises à l'expéditeur. Les trois sous-commandes marchent depuis la console.

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shcooldowns.admin` | Toute l'administration (parent des trois nœuds suivants). | `op` |
| `shcooldowns.admin.reload` | Recharger la configuration. | `op` |
| `shcooldowns.admin.info` | Consulter les cooldowns d'un joueur. | `op` |
| `shcooldowns.admin.reset` | Retirer un cooldown d'un joueur. | `op` |
| `shcooldowns.bypass.<id>` | Contourner le cooldown du groupe `<id>` (nœud construit à partir de `config.yml`, par exemple `shcooldowns.bypass.repair`). | `op` pour un id réel |
| `shcooldowns.bypass.all` | Contourner tous les cooldowns. | `false` |
| `permission:` d'une entrée | Durée réservée aux joueurs qui ont ce nœud (par exemple `shcooldowns.repairvip`). | `op` |

## Alias de commandes

La section `aliases` de `config.yml` remplace une commande tapée par sa cible avant l'exécution (`/ae` devient `/enchanter`...). Ces alias ne sont pas des commandes enregistrées : `/shcooldowns` n'en a aucun.

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
