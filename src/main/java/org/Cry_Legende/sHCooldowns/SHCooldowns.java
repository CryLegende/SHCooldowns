package org.Cry_Legende.sHCooldowns;

import org.Cry_Legende.sHCooldowns.command.ReloadCommand;
import org.Cry_Legende.sHCooldowns.config.ConfigManager;
import org.Cry_Legende.sHCooldowns.listener.PlayerCommandListener;
import org.Cry_Legende.sHCooldowns.manager.CooldownManager;
import org.Cry_Legende.sHCooldowns.placeholder.CooldownPlaceholder;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File; // Nouvel import
import java.util.Objects;
import java.util.logging.Level;

public final class SHCooldowns extends JavaPlugin {

    private ConfigManager configManager;
    private CooldownManager cooldownManager;
    private CooldownPlaceholder cooldownPlaceholder;
    private File cooldownsDataFile; // Nouveau champ pour le fichier de données

    @Override
    public void onEnable() {
        // Initialiser le fichier de données de cooldowns
        cooldownsDataFile = new File(getDataFolder(), "cooldowns_data.yml");

        // Charger la configuration du plugin (config.yml)
        try {
            configManager = new ConfigManager(this);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Impossible de charger la configuration initiale! Le plugin ne peut pas démarrer correctement.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialiser les managers
        cooldownManager = new CooldownManager(this); // Passer "this" au constructeur
        cooldownManager.loadCooldowns(cooldownsDataFile); // Charger les cooldowns persistants

        // Enregistrer les listeners
        getServer().getPluginManager().registerEvents(new PlayerCommandListener(this, configManager, cooldownManager), this);

        // Enregistrer les commandes
        Objects.requireNonNull(getCommand("shcooldowns"), "La commande shcooldowns n'est pas enregistrée dans plugin.yml !")
            .setExecutor(new ReloadCommand(this, configManager));


        // Intégration PlaceholderAPI (optionnelle)
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            getLogger().info("PlaceholderAPI trouvé ! Enregistrement de l'expansion.");
            cooldownPlaceholder = new CooldownPlaceholder(this, configManager, cooldownManager);
            if (cooldownPlaceholder.canRegister()) { // PAPI > 2.9.0
                cooldownPlaceholder.register();
                getLogger().info("Expansion PlaceholderAPI enregistrée.");
            } else {
                getLogger().warning("Impossible d'enregistrer l'expansion PAPI (PAPI < 2.9.0 ou autre problème).");
            }
        } else {
            getLogger().warning("PlaceholderAPI non trouvé. Les placeholders %shcooldowns% ne seront pas disponibles.");
            cooldownPlaceholder = null;
        }

        getLogger().info("SHCooldowns activé avec succès !");
        getLogger().info("Auteur: Cry_Legende");
    }

    @Override
    public void onDisable() {
        // Sauvegarder les cooldowns actifs
        if (cooldownManager != null) {
            cooldownManager.saveCooldowns(cooldownsDataFile);
        }

        // Désenregistrer l'expansion PAPI si elle a été enregistrée
        if (cooldownPlaceholder != null && cooldownPlaceholder.isRegistered()) { // PAPI > 2.9.0
            try {
                cooldownPlaceholder.unregister();
                getLogger().info("Expansion PlaceholderAPI désenregistrée.");
            } catch (Exception e) {
                // Ignore, PAPI est peut-être déjà désactivé.
            }
        }

        getLogger().info("SHCooldowns désactivé.");
    }

    // Getters si nécessaire
    public ConfigManager getConfigManager() {
        return configManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }
}
