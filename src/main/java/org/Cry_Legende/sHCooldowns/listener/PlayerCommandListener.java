package org.Cry_Legende.sHCooldowns.listener;

import org.Cry_Legende.sHCooldowns.SHCooldowns;
import org.Cry_Legende.sHCooldowns.config.ConfigManager;
import org.Cry_Legende.sHCooldowns.config.CooldownEntry;
import org.Cry_Legende.sHCooldowns.manager.CooldownManager;
import org.Cry_Legende.sHCooldowns.util.FormatUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;
import java.util.UUID;

public class PlayerCommandListener implements Listener {

    private final SHCooldowns plugin;
    private final ConfigManager configManager;
    private final CooldownManager cooldownManager;

    public PlayerCommandListener(SHCooldowns plugin, ConfigManager configManager, CooldownManager cooldownManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.cooldownManager = cooldownManager;
    }

    // Écouter en priorité HAUTE pour intercepter avant d'autres plugins si possible,
    // mais après les alias de base du serveur/plugins essentiels si nécessaire.
    // LOWEST pourrait être mieux pour appliquer les aliases AVANT tout autre traitement.
    // HIGHEST est souvent utilisé pour les permissions/annulations. Testez ce qui fonctionne le mieux.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    // LOWEST pour que les alias soient appliqués tôt
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String originalMessage = event.getMessage(); // Ex: "/erepair hand"
        String commandLine = originalMessage; // Commande à vérifier/utiliser pour cooldown

        // --- 1. Résolution d'Alias ---
        String targetCommand = configManager.resolveAlias(commandLine);
        if (targetCommand != null) {
            plugin.getLogger().finer("Alias résolu: '" + commandLine + "' -> '" + targetCommand + "' pour " + player.getName());
            event.setMessage(targetCommand); // Modifie la commande qui sera exécutée
            commandLine = targetCommand; // Utilise la commande cible pour les vérifications de cooldown
        }

        // Le reste de la logique (cooldown) devrait s'exécuter APRÈS les alias.
        // Si on utilisait une priorité plus haute, il faudrait un second listener ou déplacer cette logique.
        // Avec LOWEST pour les alias, on peut tout faire ici car la commande est modifiée *dans* l'event.

        // --- 2. Vérification des Cooldowns (sur la commande finale après alias) ---
        // Si la commande commence par '/', enlever le slash pour chercher dans config.
        String commandToCheck = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
        // Enlever le / pour le matching dans ConfigManager
        List<CooldownEntry> potentialEntries = configManager.findPotentialCooldownEntries(commandToCheck);

        if (potentialEntries.isEmpty()) {
            plugin.getLogger().finest("Aucune règle de cooldown trouvée pour '" + commandLine + "', ignoré.");
            return; // Aucune règle de cooldown pour cette commande
        }

        UUID playerUUID = player.getUniqueId();
        CooldownEntry activeEntry = null; // L'entrée de cooldown qui s'applique RÉELLEMENT

        // Vérifier le bypass global
        if (player.hasPermission("shcooldowns.bypass.all")) {
            plugin.getLogger().finer(player.getName() + " a la permission bypass.all, cooldown ignoré pour " + commandLine);
            return;
        }

        // Trouver la règle applicable (permission + durée la plus courte)
        CooldownEntry bestMatch = null;
        for (CooldownEntry entry : potentialEntries) {
            String requiredPermission = entry.getSpecificPermission();
            boolean hasPermission = requiredPermission == null || player.hasPermission(requiredPermission);

            // Si le joueur a la permission (ou si pas de permission requise)
            // ET que cette règle est meilleure que la précédente trouvée (ou la première)
            // "Meilleure" signifie : a une permission spécifique alors que l'autre n'en a pas, OU durée plus courte.
            if (hasPermission) {
                // Si on trouve une règle avec permission et que la précédente n'en avait pas, celle-ci est meilleure.
                // Si les deux ont/n'ont pas de perm, celle avec le cooldown le plus court est meilleure.
                // Le tri dans ConfigManager devrait déjà nous donner le meilleur en premier.
                bestMatch = entry;
                break; // On prend la première qui match car elles sont triées par priorité
            }
        }


        if (bestMatch == null) {
            // Soit aucune règle par défaut n'existe, soit le joueur n'a la permission pour aucune règle.
            // Optionnel: envoyer un message "pas de permission" si au moins UNE règle existait ?
            // Pour l'instant, on ne fait rien si aucune règle ne correspond après vérif perm.
            plugin.getLogger().finest("Aucune règle applicable trouvée pour " + player.getName() + " sur '" + commandLine + "' après vérif permission.");
            return;
        }

        activeEntry = bestMatch;
        String cooldownId = activeEntry.getId(); // Utiliser l'ID du groupe comme identifiant partagé


        // Vérifier le bypass spécifique au groupe de cooldown
        if (player.hasPermission("shcooldowns.bypass." + cooldownId)) {
            plugin.getLogger().finer(player.getName() + " a la permission bypass." + cooldownId + ", cooldown ignoré.");
            return;
        }

        // Vérifier si le joueur est en cooldown pour ce groupe
        long remainingMillis = cooldownManager.getRemainingCooldown(playerUUID, cooldownId);

        if (remainingMillis > 0) {
            // Le joueur est en cooldown
            String formattedTime = FormatUtil.formatDuration(remainingMillis);
            String message = FormatUtil.colorize(configManager.getCooldownMessage().replace("%time%", formattedTime));
            player.sendMessage(message);
            event.setCancelled(true); // Annule l'exécution de la commande
            plugin.getLogger().finer(player.getName() + " est en cooldown pour " + cooldownId + " (" + formattedTime + " restant). Commande '" + commandLine + "' annulée.");
        } else {
            // Pas en cooldown, appliquer le nouveau cooldown basé sur la règle active
            long duration = activeEntry.getCooldownMillis();
            cooldownManager.setCooldown(playerUUID, cooldownId, duration);
            plugin.getLogger().finer(player.getName() + " a utilisé " + commandLine + ", cooldown " + cooldownId + " appliqué pour " + FormatUtil.formatDuration(duration));
        }
    }
}
