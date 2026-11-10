package org.Cry_Legende.sHCooldowns;

import org.Cry_Legende.sHCooldowns.command.ReloadCommand;
import org.Cry_Legende.sHCooldowns.config.ConfigManager;
import org.Cry_Legende.sHCooldowns.listener.PlayerCommandListener;
import org.Cry_Legende.sHCooldowns.manager.CooldownManager;
import org.Cry_Legende.sHCooldowns.network.CooldownDatabase;
import org.Cry_Legende.sHCooldowns.network.NetworkCooldowns;
import org.Cry_Legende.sHCooldowns.network.NetworkLink;
import org.Cry_Legende.sHCooldowns.network.ShNetworkLink;
import org.Cry_Legende.sHCooldowns.placeholder.CooldownPlaceholder;
import org.Cry_Legende.sHCooldowns.util.FormatUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File; // Nouvel import
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.logging.Level;

public final class SHCooldowns extends JavaPlugin {

    private ConfigManager configManager;
    private CooldownManager cooldownManager;
    private CooldownPlaceholder cooldownPlaceholder;
    private File cooldownsDataFile; // Nouveau champ pour le fichier de données
    // Non nul seulement avec SHNetwork : les cooldowns passent aussi par la base partagée
    private NetworkCooldowns network;
    private YamlConfiguration messages;

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

        loadMessages();

        // Initialiser les managers
        cooldownManager = new CooldownManager(this); // Passer "this" au constructeur
        cooldownManager.loadCooldowns(cooldownsDataFile); // Charger les cooldowns persistants
        enableNetwork();

        // Toutes les 5 minutes : purge des cooldowns expirés et sauvegarde (un arrêt brutal ne perd plus les cooldowns en cours)
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            cooldownManager.cleanupExpiredCooldowns();
            cooldownManager.saveCooldowns(cooldownsDataFile, true);
            if (network != null) network.purge();
        }, 6000L, 6000L);

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
        if (network != null) {
            network.stop();
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

    /**
     * Passe en mode réseau si SHNetwork est installé et 'reseau.actif' est vrai. Les classes qui touchent à SHNetwork
     * ne sont chargées qu'ici : sans lui, le plugin se comporte exactement comme avant.
     */
    private void enableNetwork() {
        if (!getConfig().getBoolean("reseau.actif", true)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("SHNetwork")) return;
        CooldownDatabase db = null;
        try {
            NetworkLink link = ShNetworkLink.open();
            db = new CooldownDatabase(
                getConfig().getString("reseau.base-de-donnees.url"),
                getConfig().getString("reseau.base-de-donnees.utilisateur"),
                getConfig().getString("reseau.base-de-donnees.mot-de-passe"),
                Math.max(250, getConfig().getInt("reseau.base-de-donnees.delai-ms", 3000)));
            db.createTable();
            Duration wait = Duration.ofSeconds(Math.max(1, getConfig().getInt("reseau.attente-max-secondes", 10)));
            NetworkCooldowns net = new NetworkCooldowns(this, cooldownManager, db, link, wait);
            net.start();
            network = net;
            getLogger().info("SHNetwork détecté : les cooldowns sont partagés entre les serveurs (base partagée).");
        } catch (Exception | LinkageError e) {
            if (db != null) db.close();
            getLogger().log(Level.SEVERE, "SHNetwork présent mais inutilisable, cooldowns locaux à ce serveur : " + e);
        }
    }

    /** messages.yml, avec les valeurs du jar pour toute clé absente du fichier du serveur. */
    private void loadMessages() {
        File file = new File(getDataFolder(), "messages.yml");
        if (!file.exists()) saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(file);
        try (InputStreamReader defaults = new InputStreamReader(Objects.requireNonNull(getResource("messages.yml")), StandardCharsets.UTF_8)) {
            messages.setDefaults(YamlConfiguration.loadConfiguration(defaults));
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "messages.yml de référence illisible : " + e.getMessage());
        }
    }

    /** Texte de messages.yml, couleurs appliquées. */
    public String getMessage(String key) {
        return FormatUtil.colorize(messages.getString(key, key));
    }

    /** Mode réseau actif, ou null sur un serveur seul. */
    public NetworkCooldowns getNetwork() {
        return network;
    }

    // Getters si nécessaire
    public ConfigManager getConfigManager() {
        return configManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }
}
