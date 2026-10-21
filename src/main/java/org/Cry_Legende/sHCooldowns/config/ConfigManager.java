package org.Cry_Legende.sHCooldowns.config;

import org.Cry_Legende.sHCooldowns.SHCooldowns;
import org.Cry_Legende.sHCooldowns.util.TimeUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ConfigManager {

    private final SHCooldowns plugin;
    private FileConfiguration config;

    private String cooldownMessage;
    private String noPermissionMessage; // Ajouté
    private String placeholderReadyMessage;

    // Stocke toutes les entrées de cooldown, peu importe la permission
    private final Map<String, List<CooldownEntry>> cooldownGroups = new HashMap<>();
    // Stocke les aliases normalisés (lowercase)
    private final Map<String, String> aliases = new HashMap<>();

    public ConfigManager(SHCooldowns plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig(); // Recharge depuis le disque
        this.config = plugin.getConfig();

        // Effacer les anciennes données avant de recharger
        cooldownGroups.clear();
        aliases.clear();

        // Charger les messages
        cooldownMessage = config.getString("cooldown_message", "&cVous devez attendre %time% avant d'utiliser cette commande à nouveau.");
        noPermissionMessage = config.getString("no_permission_message", "&cVous n'avez pas la permission d'utiliser cette commande.");
        placeholderReadyMessage = config.getString("placeholder_ready_message", "Prêt");


        // Charger les Cooldowns
        ConfigurationSection cooldownsSection = config.getConfigurationSection("cooldowns");
        if (cooldownsSection != null) {
            for (String groupId : cooldownsSection.getKeys(false)) {
                ConfigurationSection groupSection = cooldownsSection.getConfigurationSection(groupId);
                if (groupSection == null) continue;

                List<CooldownEntry> entriesForGroup = new ArrayList<>();
                for (String entryKey : groupSection.getKeys(false)) { // ex: "default", "vip"
                    ConfigurationSection entrySection = groupSection.getConfigurationSection(entryKey);
                    if (entrySection == null) continue;

                    String cooldownString = entrySection.getString("cooldown");
                    long cooldownMillis = TimeUtil.parseTime(cooldownString);
                    if (cooldownMillis <= 0) {
                        plugin.getLogger().warning("Cooldown invalide ou nul pour '" + groupId + "." + entryKey + "' : '" + cooldownString + "'. Cette entrée sera ignorée.");
                        continue;
                    }

                    String permission = entryKey.equalsIgnoreCase("default") ? null : entrySection.getString("permission");
                    // Si ce n'est pas default mais que la perm est vide, on utilise une perm basée sur l'id+key
                    if (permission == null && !entryKey.equalsIgnoreCase("default")) {
                        permission = entrySection.getString("permission", "shcooldowns." + groupId + "." + entryKey); // Fallback permission auto-générée
                        plugin.getLogger().config("Permission auto-générée pour " + groupId + "." + entryKey + ": " + permission);
                    }


                    List<String> sharedCommands = entrySection.getStringList("shared_commands");
                    if (sharedCommands.isEmpty()) {
                        plugin.getLogger().warning("Aucune 'shared_commands' définie pour '" + groupId + "." + entryKey + "'. Cette entrée sera ignorée.");
                        continue;
                    }

                    // Nettoyer et normaliser les commandes partagées
                    List<String> normalizedSharedCommands = sharedCommands.stream()
                        .map(cmd -> cmd.trim().toLowerCase())
                        .filter(cmd -> !cmd.isEmpty())
                        // Ajouter le / s'il manque au début, sauf pour le wildcard seul "*"
                        .map(cmd -> (!cmd.startsWith("/") && !cmd.equals("*")) ? "/" + cmd : cmd)
                        .collect(Collectors.toList());


                    if (normalizedSharedCommands.isEmpty()) {
                        plugin.getLogger().warning("Après normalisation, aucune 'shared_commands' valide pour '" + groupId + "." + entryKey + "'. Cette entrée sera ignorée.");
                        continue;
                    }

                    CooldownEntry entry = new CooldownEntry(groupId, permission, cooldownMillis, normalizedSharedCommands);
                    entriesForGroup.add(entry);
                    plugin.getLogger().config("Chargé Cooldown: ID=" + groupId + ", Perm=" + permission + ", Temps=" + cooldownMillis + "ms, Commandes=" + normalizedSharedCommands);

                }
                // Trier les entrées d'un groupe : celles avec permission spécifique d'abord, puis par durée (plus court d'abord)
                entriesForGroup.sort((e1, e2) -> {
                    boolean p1Exists = e1.getSpecificPermission() != null;
                    boolean p2Exists = e2.getSpecificPermission() != null;
                    if (p1Exists && !p2Exists) return -1; // e1 avec perm avant e2 sans perm
                    if (!p1Exists && p2Exists) return 1;  // e2 avec perm avant e1 sans perm
                    // Si les deux ont/n'ont pas de perm, trier par durée (le plus court en premier)
                    return Long.compare(e1.getCooldownMillis(), e2.getCooldownMillis());
                });

                if (!entriesForGroup.isEmpty()) {
                    cooldownGroups.put(groupId.toLowerCase(), entriesForGroup);
                }
            }
        } else {
            plugin.getLogger().warning("Section 'cooldowns' non trouvée ou vide dans config.yml.");
        }


        ConfigurationSection aliasesSection = config.getConfigurationSection("aliases");
        if (aliasesSection != null) {
            // Charger d'abord les alias spécifiques, puis ceux avec wildcard
            List<String> specificAliases = new ArrayList<>();
            List<String> wildcardAliases = new ArrayList<>();

            for (String alias : aliasesSection.getKeys(false)) {
                if (alias.contains("*")) {
                    wildcardAliases.add(alias);
                } else {
                    specificAliases.add(alias);
                }
            }
            // Trier les spécifiques par longueur décroissante pour matcher le plus long d'abord
            specificAliases.sort((s1, s2) -> Integer.compare(s2.length(), s1.length()));
            // Trier les wildcards par longueur de préfixe décroissante
            wildcardAliases.sort((s1, s2) -> Integer.compare(s2.indexOf('*'), s1.indexOf('*')));

            // Plus besoin de cette interface personnalisée :
            // @FunctionalInterface
            // private interface LoadAliasFunction {
            //    void load(String key);
            // }

            // Utiliser directement Consumer<String>
            Consumer<String> loadAliasAction = key -> { // Renommé pour éviter confusion avec une variable "loadAlias" potentielle
                String target = aliasesSection.getString(key);
                if (target != null && !target.isEmpty()) {
                    String normalizedAlias = key.trim().toLowerCase();
                    // Ajouter le / s'il manque
                    if (!normalizedAlias.startsWith("/") && !normalizedAlias.equals("*")) {
                        normalizedAlias = "/" + normalizedAlias;
                    }
                    String normalizedTarget = target.trim().toLowerCase();
                    if (!normalizedTarget.startsWith("/")) {
                        normalizedTarget = "/" + normalizedTarget;
                    }

                    aliases.put(normalizedAlias, normalizedTarget);
                    plugin.getLogger().config("Chargé Alias: '" + normalizedAlias + "' -> '" + normalizedTarget + "'");
                } else {
                    plugin.getLogger().warning("Alias invalide ou cible vide pour '" + key + "'. Ignoré.");
                }
            };

            // Charger les alias spécifiques d'abord, puis les wildcards
            specificAliases.forEach(loadAliasAction);
            wildcardAliases.forEach(loadAliasAction);

        } else {
            plugin.getLogger().warning("Section 'aliases' non trouvée ou vide dans config.yml.");
        }

        plugin.getLogger().info("Configuration rechargée. " + cooldownGroups.size() + " groupe(s) de cooldowns et " + aliases.size() + " alias chargés.");
    }

    // Méthode privée pour l'interface de la fonction de chargement
    @FunctionalInterface
    private interface LoadAliasFunction {
        void load(String key);
    }


    /**
     * Trouve TOUTES les entrées de cooldown (default + par permission) qui pourraient
     * correspondre à la commande donnée. Le Listener devra ensuite choisir la meilleure.
     */
    public List<CooldownEntry> findPotentialCooldownEntries(String commandLine) {
        List<CooldownEntry> matchingEntries = new ArrayList<>();
        String normalizedCommand = commandLine.toLowerCase().trim();
        if (!normalizedCommand.startsWith("/")) {
            normalizedCommand = "/" + normalizedCommand;
        }

        // Itérer sur tous les groupes et leurs entrées
        for (List<CooldownEntry> groupEntries : cooldownGroups.values()) {
            for (CooldownEntry entry : groupEntries) {
                if (entry.matches(normalizedCommand)) {
                    matchingEntries.add(entry);
                    // Important: On ne s'arrête pas, car plusieurs règles (default, vip) peuvent matcher la même commande.
                    // La logique de sélection se fera DANS le listener en fonction des permissions du joueur.
                }
            }
        }
        // On trie ici pour s'assurer que la plus spécifique est en premier (même si déjà trié par groupe)
        // Priorité: 1. Avec permission 2. Durée la plus courte
        matchingEntries.sort((e1, e2) -> {
            boolean p1Exists = e1.getSpecificPermission() != null;
            boolean p2Exists = e2.getSpecificPermission() != null;
            int permCompare = Boolean.compare(p2Exists, p1Exists); // Inverse: true (avec perm) vient avant false
            if (permCompare != 0) return permCompare;

            // Si permissions similaires (les deux ont/n'ont pas), trier par longueur de pattern (plus spécifique d'abord)
            // C'est TRÈS complexe à définir correctement quelle règle est "plus spécifique"
            // (/cmd abc vs /cmd *). Simplifions pour l'instant : on priorise par permission puis par durée
            return Long.compare(e1.getCooldownMillis(), e2.getCooldownMillis());
        });

        return matchingEntries;
    }


    /**
     * Trouve une entrée de cooldown pour PAPI en se basant sur une correspondance exacte de la commande (sans args pour le moment)
     * TODO: Améliorer pour PAPI avec args ou wildcard
     */
    public CooldownEntry findExactCooldownEntryForPapi(String commandIdentifier) {
        String normalizedCommand = commandIdentifier.toLowerCase().trim();
        if (!normalizedCommand.startsWith("/")) {
            normalizedCommand = "/" + normalizedCommand;
        }

        // On cherche une entrée dont l'UN des shared_commands match EXACTEMENT
        for (List<CooldownEntry> groupEntries : cooldownGroups.values()) {
            for (CooldownEntry entry : groupEntries) {
                // On cherche une correspondance exacte sur l'id de groupe pour PAPI
                if (normalizedCommand.equals("/" + entry.getId().toLowerCase())) {
                    // On retourne la première trouvée qui correspond à l'ID (la logique de PAPI demandera sûrement par ID)
                    // Peut-être faudrait-il retourner la default ? Ou celle avec la perm la plus probable ?
                    // Pour l'instant, on retourne la première qui match l'ID pour avoir AU MOINS une durée
                    return entry; // Simple mais potentiellement pas la bonne durée si perms différentes
                }
                // Essayer aussi un match exact sur les shared patterns si l'id ne match pas directement
                  /* Non, PAPI utilisera probablement %shcooldowns_time_repair% où 'repair' est l'ID
                  if (entry.commandExactlyMatchesPattern(normalizedCommand)) {
                     return entry;
                  }
                  */
            }
        }
        return null; // Aucune entrée trouvée pour cet ID/commande PAPI
    }

    /**
     * Recherche la cible d'un alias. Gère les alias spécifiques et ceux avec wildcard.
     *
     * @param commandLine La ligne de commande complète tapée par le joueur (ex: "/erepair hand").
     * @return La commande cible si un alias est trouvé (ex: "/repair"), ou null sinon.
     */
    public String resolveAlias(String commandLine) {
        String normalizedCommand = commandLine.trim().toLowerCase();
        // Pas besoin d'ajouter '/' ici, car les clés dans `aliases` l'ont déjà.

        // 1. Chercher une correspondance exacte dans les alias (les plus longs d'abord grâce au tri)
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            String alias = entry.getKey();
            if (!alias.contains("*")) { // Alias spécifique
                // Gérer les arguments: si la commande tapée COMMENCE par l'alias exact
                // ET est soit identique, soit suivie d'un espace.
                if (normalizedCommand.equals(alias) || normalizedCommand.startsWith(alias + " ")) {
                    // Reconstruire la commande cible avec les arguments restants
                    String remainingArgs = "";
                    if (normalizedCommand.length() > alias.length()) {
                        remainingArgs = normalizedCommand.substring(alias.length()); // Garde l'espace potentiel au début
                    }
                    return entry.getValue() + remainingArgs; // Retourne "/target" ou "/target arg1 arg2"
                }
            }
        }

        // 2. Si aucune correspondance exacte, chercher les wildcards (triés par longueur de préfixe)
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            String alias = entry.getKey();
            if (alias.contains("*")) { // Alias wildcard
                String prefix = alias.substring(0, alias.indexOf('*')).trim(); // Partie avant '*'
                // Vérifie si la commande commence par le préfixe
                if (normalizedCommand.startsWith(prefix)) {
                    // Note: la cible est fixée, on ignore les arguments après le wildcard dans la source pour l'instant
                    // Pour l'instant, on retourne JUSTE la cible définie dans config.yml
                    // Ex: /efix * -> /repair all (même si on a tapé /efix machin chose)
                    // Pour être plus flexible, il faudrait capturer les arguments après le prefix
                    // et les ajouter à la cible si la cible contient aussi un placeholder.
                    // Simplification: retourne la cible directe.
                    return entry.getValue();
                }
            }
        }

        return null; // Aucun alias trouvé
    }


    public String getCooldownMessage() {
        return cooldownMessage;
    }

    public String getNoPermissionMessage() {
        return noPermissionMessage;
    }

    public String getPlaceholderReadyMessage() {
        return placeholderReadyMessage;
    }
}
