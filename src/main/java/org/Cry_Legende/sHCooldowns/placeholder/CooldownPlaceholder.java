package org.Cry_Legende.sHCooldowns.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.Cry_Legende.sHCooldowns.SHCooldowns;
import org.Cry_Legende.sHCooldowns.config.ConfigManager;
import org.Cry_Legende.sHCooldowns.config.CooldownEntry;
import org.Cry_Legende.sHCooldowns.manager.CooldownManager;
import org.Cry_Legende.sHCooldowns.util.FormatUtil;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CooldownPlaceholder extends PlaceholderExpansion {

    private final SHCooldowns plugin;
    private final ConfigManager configManager;
    private final CooldownManager cooldownManager;

    public CooldownPlaceholder(SHCooldowns plugin, ConfigManager configManager, CooldownManager cooldownManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.cooldownManager = cooldownManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "shcooldowns"; // Préfixe pour les placeholders: %shcooldowns_...%
    }

    @Override
    public @NotNull String getAuthor() {
        return "Cry_Legende";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Peut être mis à jour à la demande
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || !player.isOnline()) {
            return null; // Ne peut pas récupérer le cooldown d'un joueur hors ligne facilement sans BDD
        }

        // params contient ce qui suit "shcooldowns_"
        // Exemple : %shcooldowns_time_repair% -> params = "time_repair"
        // Exemple : %shcooldowns_time_repair_all% -> params = "time_repair_all"

        if (params.toLowerCase().startsWith("time_")) {
            String cooldownId = params.substring(5); // Enlève "time_"

            if (cooldownId.isEmpty()) {
                return "ID Cooldown manquant";
            }

            // Vérifier si le joueur est sur cooldown pour cet ID
            long remainingMillis = cooldownManager.getRemainingCooldown(player.getUniqueId(), cooldownId);

            if (remainingMillis > 0) {
                return FormatUtil.formatDuration(remainingMillis); // "1h 5m 10s"
            } else {
                // Pas en cooldown
                return FormatUtil.colorize(configManager.getPlaceholderReadyMessage()); // "Prêt" (configurable)
            }
        }

        return null; // Placeholder non reconnu
    }
}