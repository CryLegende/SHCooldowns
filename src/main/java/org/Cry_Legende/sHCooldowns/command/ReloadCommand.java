package org.Cry_Legende.sHCooldowns.command;

import org.Cry_Legende.sHCooldowns.SHCooldowns;
import org.Cry_Legende.sHCooldowns.config.ConfigManager;
import org.Cry_Legende.sHCooldowns.util.FormatUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ReloadCommand implements CommandExecutor {

    private final SHCooldowns plugin;
    private final ConfigManager configManager;

    public ReloadCommand(SHCooldowns plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("shcooldowns.admin")) {
            sender.sendMessage(FormatUtil.colorize("&cVous n'avez pas la permission d'exécuter cette commande."));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            try {
                configManager.loadConfig();
                sender.sendMessage(FormatUtil.colorize("&a[SHCooldowns] Configuration rechargée avec succès !"));
            } catch (Exception e) {
                sender.sendMessage(FormatUtil.colorize("&c[SHCooldowns] Une erreur est survenue lors du rechargement de la configuration. Vérifiez la console."));
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Erreur lors du rechargement de la configuration de SHCooldowns:", e);
            }
            return true;
        }

        sender.sendMessage(FormatUtil.colorize("&cUsage: /" + label + " reload"));
        return true;
    }
}