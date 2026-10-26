package org.Cry_Legende.sHCooldowns.manager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.Cry_Legende.sHCooldowns.SHCooldowns; // Importer la classe principale pour le logger

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class CooldownManager {

    private final SHCooldowns plugin; // Ajout d'une référence au plugin pour le logger
    // Stocke les cooldowns actifs : UUID Joueur -> Map<Cooldown ID, Timestamp Fin Cooldown>
    private final Map<UUID, Map<String, Long>> activeCooldowns = new ConcurrentHashMap<>();

    public CooldownManager(SHCooldowns plugin) { // Modifier le constructeur
        this.plugin = plugin;
    }


    /**
     * Applique un cooldown à un joueur pour un groupe de commandes spécifique.
     *
     * @param playerUUID     UUID du joueur.
     * @param cooldownId     L'identifiant unique du groupe de cooldown (ex: "repair", "repair_all").
     * @param durationMillis Durée du cooldown en millisecondes.
     */
    public void setCooldown(UUID playerUUID, String cooldownId, long durationMillis) {
        if (durationMillis <= 0) return; // Ne pas mettre de cooldown négatif ou nul

        long expiryTime = System.currentTimeMillis() + durationMillis;
        String idLower = cooldownId.toLowerCase(Locale.ROOT); // Utiliser ID en minuscule comme clé

        // Atomique par joueur : une purge concurrente (placeholder asynchrone) ne peut pas jeter la map qu'on remplit
        activeCooldowns.compute(playerUUID, (k, map) -> {
            if (map == null) map = new ConcurrentHashMap<>();
            map.put(idLower, expiryTime);
            return map;
        });
    }

    /**
     * Vérifie si un joueur est actuellement en cooldown pour un groupe de commandes spécifique.
     *
     * @param playerUUID UUID du joueur.
     * @param cooldownId L'identifiant unique du groupe de cooldown.
     * @return true si le joueur est en cooldown, false sinon.
     */
    public boolean isOnCooldown(UUID playerUUID, String cooldownId) {
        return getRemainingCooldown(playerUUID, cooldownId) > 0;
    }

    /**
     * Obtient le temps de cooldown restant pour un joueur et un groupe de commandes.
     *
     * @param playerUUID UUID du joueur.
     * @param cooldownId L'identifiant unique du groupe de cooldown.
     * @return Le temps restant en millisecondes, ou 0 si pas en cooldown ou expiré.
     */
    public long getRemainingCooldown(UUID playerUUID, String cooldownId) {
        String idLower = cooldownId.toLowerCase(Locale.ROOT);
        Map<String, Long> playerCooldowns = activeCooldowns.get(playerUUID);

        if (playerCooldowns != null) {
            Long expiryTime = playerCooldowns.get(idLower);
            if (expiryTime != null) {
                long remaining = expiryTime - System.currentTimeMillis();
                if (remaining <= 0) {
                    // Cooldown expiré, le nettoyer
                    removeCooldown(playerUUID, idLower, expiryTime);
                    return 0;
                }
                return remaining;
            }
        }
        return 0; // Pas de cooldown trouvé
    }

    /**
     * Supprime un cooldown spécifique pour un joueur (utilisé quand le temps est écoulé).
     * Seulement si l'échéance est toujours celle qu'on a lue : un cooldown reposé entre-temps est conservé.
     *
     * @param playerUUID UUID du joueur.
     * @param cooldownId L'identifiant unique du groupe de cooldown.
     * @param expiry     Échéance lue, expirée.
     */
    private void removeCooldown(UUID playerUUID, String cooldownId, long expiry) {
        String idLower = cooldownId.toLowerCase(Locale.ROOT);
        activeCooldowns.computeIfPresent(playerUUID, (k, map) -> {
            map.remove(idLower, expiry);
            // Si le joueur n'a plus aucun cooldown, on supprime son entrée de la map principale
            return map.isEmpty() ? null : map;
        });
    }

    // Purge des cooldowns expirés : les joueurs hors ligne ne repassent jamais par getRemainingCooldown
    public void cleanupExpiredCooldowns() {
        long now = System.currentTimeMillis();
        for (UUID uuid : activeCooldowns.keySet()) {
            activeCooldowns.computeIfPresent(uuid, (k, map) -> {
                map.values().removeIf(expiry -> expiry <= now);
                return map.isEmpty() ? null : map;
            });
        }
    }

    /**
     * Sauvegarde les cooldowns actifs dans un fichier.
     *
     * @param dataFile Le fichier où sauvegarder les données.
     */
    public synchronized void saveCooldowns(File dataFile) {
        saveCooldowns(dataFile, false);
    }

    /**
     * @param quiet pas de message en console (sauvegarde périodique)
     */
    public synchronized void saveCooldowns(File dataFile, boolean quiet) {
        YamlConfiguration cooldownsConfig = new YamlConfiguration();
        int savedCount = 0;
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<UUID, Map<String, Long>> playerEntry : activeCooldowns.entrySet()) {
            UUID playerUUID = playerEntry.getKey();
            Map<String, Long> playerCooldowns = playerEntry.getValue();
            Map<String, Long> validCooldownsToSave = new HashMap<>();

            for (Map.Entry<String, Long> cooldownEntry : playerCooldowns.entrySet()) {
                // On ne sauvegarde que les cooldowns qui ne sont pas encore expirés
                if (cooldownEntry.getValue() > currentTime) {
                    validCooldownsToSave.put(cooldownEntry.getKey(), cooldownEntry.getValue());
                }
            }

            if (!validCooldownsToSave.isEmpty()) {
                // Conversion UUID en String pour la clé YAML
                // Stocker sous la forme playerUUID.cooldownId = expirationTime
                for (Map.Entry<String, Long> entry : validCooldownsToSave.entrySet()) {
                    cooldownsConfig.set(playerUUID.toString() + "." + entry.getKey(), entry.getValue());
                    savedCount++;
                }
            }
        }

        if (savedCount > 0) {
            try {
                cooldownsConfig.save(dataFile);
                if (!quiet) plugin.getLogger().info("Sauvegardé " + savedCount + " cooldown(s) actif(s) dans " + dataFile.getName());
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Impossible de sauvegarder les cooldowns dans " + dataFile.getName(), e);
            }
        } else {
            if (!quiet) plugin.getLogger().info("Aucun cooldown actif à sauvegarder.");
            // Si le fichier existe et est vide, on peut le supprimer pour nettoyer
            if (dataFile.exists() && dataFile.length() == 0) {
                dataFile.delete();
            } else if (dataFile.exists() && savedCount == 0) {
                // Si des données existaient mais plus rien d'actif, on vide le fichier
                try {
                    cooldownsConfig.save(dataFile); // sauvegarde un fichier vide
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Impossible de vider le fichier des cooldowns: " + dataFile.getName(), e);
                }
            }
        }
    }

    /**
     * Charge les cooldowns depuis un fichier.
     *
     * @param dataFile Le fichier depuis lequel charger les données.
     */
    public void loadCooldowns(File dataFile) {
        if (!dataFile.exists()) {
            plugin.getLogger().info("Aucun fichier de données de cooldowns (" + dataFile.getName() + ") trouvé, aucun cooldown chargé.");
            return;
        }

        YamlConfiguration cooldownsConfig = YamlConfiguration.loadConfiguration(dataFile);
        int loadedCount = 0;
        int expiredCount = 0;
        long currentTime = System.currentTimeMillis();

        for (String playerUUIDString : cooldownsConfig.getKeys(false)) {
            try {
                UUID playerUUID = UUID.fromString(playerUUIDString);
                Map<String, Long> playerLoadedCooldowns = new ConcurrentHashMap<>();

                for (String cooldownId : cooldownsConfig.getConfigurationSection(playerUUIDString).getKeys(false)) {
                    long expiryTime = cooldownsConfig.getLong(playerUUIDString + "." + cooldownId);

                    if (expiryTime > currentTime) { // Ne charge que les cooldowns encore valides
                        playerLoadedCooldowns.put(cooldownId.toLowerCase(Locale.ROOT), expiryTime);
                        loadedCount++;
                    } else {
                        expiredCount++;
                    }
                }

                if (!playerLoadedCooldowns.isEmpty()) {
                    activeCooldowns.put(playerUUID, playerLoadedCooldowns);
                }

            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID invalide '" + playerUUIDString + "' trouvé dans " + dataFile.getName() + ". Entrée ignorée.");
            } catch (NullPointerException e) {
                plugin.getLogger().warning("Structure invalide pour l'UUID '" + playerUUIDString + "' dans " + dataFile.getName() + ". Entrée ignorée.");
            }
        }
        if (loadedCount > 0) {
            plugin.getLogger().info("Chargé " + loadedCount + " cooldown(s) depuis " + dataFile.getName() + ".");
        }
        if (expiredCount > 0) {
            plugin.getLogger().info(expiredCount + " cooldown(s) expiré(s) n'ont pas été rechargés depuis " + dataFile.getName() + ".");
        }
        if (loadedCount == 0 && expiredCount == 0) {
            plugin.getLogger().info("Le fichier de données de cooldowns (" + dataFile.getName() + ") était vide ou ne contenait que des données expirées.");
        }
    }
}
