package org.Cry_Legende.sHCooldowns.network;

import org.Cry_Legende.sHCooldowns.SHCooldowns;
import org.Cry_Legende.sHCooldowns.manager.CooldownManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Mode réseau : les cooldowns passent aussi par la base partagée, pour qu'un cooldown pris sur un serveur
 * bloque la commande sur les autres.
 *
 * <ul>
 *   <li>à la pose : écriture en base, sur un fil à part (jamais le fil principal) ;</li>
 *   <li>au départ du joueur : la barrière de SHNetwork attend que ces écritures soient finies ;</li>
 *   <li>à l'arrivée : une fois le départ confirmé, les cooldowns du joueur sont relus en base et fusionnés
 *       avec ceux de la mémoire (l'échéance la plus tardive gagne).</li>
 * </ul>
 *
 * Les commandes soumises à un cooldown sont refusées le temps de cette lecture, sinon un joueur qui arrive
 * pourrait passer devant un cooldown qui n'est pas encore chargé.
 */
public final class NetworkCooldowns implements Listener, CooldownManager.Sink {

    private static final int WRITE_ATTEMPTS = 3;

    private final SHCooldowns plugin;
    private final CooldownManager manager;
    private final CooldownDatabase db;
    private final NetworkLink link;
    private final Duration waitMax;
    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SHCooldowns-reseau");
        thread.setDaemon(true);
        return thread;
    });
    /** Joueurs dont les cooldowns de la base ne sont pas encore chargés. */
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();

    public NetworkCooldowns(SHCooldowns plugin, CooldownManager manager, CooldownDatabase db, NetworkLink link, Duration waitMax) {
        this.plugin = plugin;
        this.manager = manager;
        this.db = db;
        this.link = link;
        this.waitMax = waitMax;
    }

    /** Branche le mode réseau : écoute des connexions, écriture à la pose, sauvegarde au départ. */
    public void start() {
        manager.setSink(this);
        link.registerSaveTask(player -> flush());
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // les joueurs déjà là (rechargement à chaud) sont chargés comme à une arrivée
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            beginLoad(online.getUniqueId());
        }
    }

    /** Termine les écritures en cours puis ferme la base. */
    public void stop() {
        manager.setSink(null);
        io.shutdown();
        try {
            if (!io.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Des écritures de cooldowns n'ont pas pu être terminées à l'arrêt.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        db.close();
    }

    /** Vrai tant que les cooldowns du joueur ne sont pas chargés depuis la base. */
    public boolean isLoading(UUID uuid) {
        return loading.contains(uuid);
    }

    /** Purge, hors du fil principal, les cooldowns échus de la base. */
    public void purge() {
        submit(() -> {
            try {
                db.purge(System.currentTimeMillis());
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Purge des cooldowns en base impossible : " + e.getMessage());
            }
        });
    }

    @Override
    public void onSet(UUID uuid, String id, long expiry) {
        submit(() -> write(uuid, id, expiry));
    }

    @Override
    public void onClear(UUID uuid, String id) {
        submit(() -> {
            try {
                db.delete(uuid, id);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Cooldowns de " + uuid + " non supprimés en base : " + e.getMessage());
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        beginLoad(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        loading.remove(event.getPlayer().getUniqueId());
    }

    private void beginLoad(UUID uuid) {
        loading.add(uuid);
        link.awaitSaved(uuid, waitMax).whenComplete((saved, error) -> {
            if (error != null || !Boolean.TRUE.equals(saved)) {
                plugin.getLogger().warning("Départ de " + uuid + " non confirmé par l'autre serveur"
                    + (error != null ? " (" + error + ")" : "") + " : cooldowns relus quand même.");
            }
            submit(() -> load(uuid));
        });
    }

    private void load(UUID uuid) {
        try {
            Map<String, Long> rows = db.load(uuid, System.currentTimeMillis());
            manager.merge(uuid, rows);
        } catch (SQLException e) {
            // le joueur reste libre plutôt que bloqué : les cooldowns de ce serveur s'appliquent quand même
            plugin.getLogger().log(Level.WARNING, "Cooldowns de " + uuid + " non relus en base : " + e.getMessage());
        } finally {
            loading.remove(uuid);
        }
    }

    private void write(UUID uuid, String id, long expiry) {
        for (int attempt = 1; attempt <= WRITE_ATTEMPTS; attempt++) {
            try {
                db.upsert(uuid, id, expiry);
                return;
            } catch (SQLException e) {
                if (attempt == WRITE_ATTEMPTS) {
                    plugin.getLogger().log(Level.WARNING, "Cooldown " + id + " de " + uuid + " non écrit en base : " + e.getMessage());
                    return;
                }
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /** Futur terminé quand toutes les écritures déjà demandées sont faites (le fil d'écriture les traite dans l'ordre). */
    private CompletableFuture<Void> flush() {
        CompletableFuture<Void> done = new CompletableFuture<>();
        try {
            io.execute(() -> done.complete(null));
        } catch (RejectedExecutionException stopped) {
            done.complete(null);
        }
        return done;
    }

    private void submit(Runnable task) {
        try {
            io.execute(task);
        } catch (RejectedExecutionException stopped) {
            // plugin arrêté : plus rien à écrire
        }
    }
}
