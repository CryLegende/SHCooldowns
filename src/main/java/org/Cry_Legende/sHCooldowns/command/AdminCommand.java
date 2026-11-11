package org.Cry_Legende.sHCooldowns.command;

import org.Cry_Legende.sHCooldowns.SHCooldowns;
import org.Cry_Legende.sHCooldowns.util.FormatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /shcooldowns reload|info|reset */
public class AdminCommand implements TabExecutor {

    private static final String ADMIN = "shcooldowns.admin";
    private static final List<String> SUBCOMMANDS = List.of("reload", "info", "reset");

    private final SHCooldowns plugin;

    public AdminCommand(SHCooldowns plugin) {
        this.plugin = plugin;
    }

    private static boolean can(CommandSender sender, String sub) {
        return sender.hasPermission(ADMIN + "." + sub) || sender.hasPermission(ADMIN);
    }

    private static List<String> allowed(CommandSender sender) {
        return SUBCOMMANDS.stream().filter(sub -> can(sender, sub)).toList();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> allowed = allowed(sender);
        if (allowed.isEmpty()) {
            sender.sendMessage(FormatUtil.colorize(plugin.getConfigManager().getNoPermissionMessage()));
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (!allowed.contains(sub)) {
            if (SUBCOMMANDS.contains(sub)) {
                sender.sendMessage(FormatUtil.colorize(plugin.getConfigManager().getNoPermissionMessage()));
            } else {
                sender.sendMessage(plugin.getMessage("usage", "%subcommands%", String.join("|", allowed)));
            }
            return true;
        }
        switch (sub) {
            case "reload" -> reload(sender);
            case "info" -> info(sender, args);
            default -> reset(sender, args);
        }
        return true;
    }

    private void reload(CommandSender sender) {
        List<String> errors = plugin.reloadAll();
        if (!errors.isEmpty()) {
            for (String error : errors) {
                int split = error.indexOf('\n');
                sender.sendMessage(plugin.getMessage("reload-error", "%file%", error.substring(0, split), "%error%", error.substring(split + 1)));
            }
            return;
        }
        sender.sendMessage(plugin.getMessage("reload-ok", "%groups%", String.valueOf(plugin.getConfigManager().getGroupCount()),
            "%aliases%", String.valueOf(plugin.getConfigManager().getAliasCount())));
        sender.sendMessage(plugin.getMessage("reload-network"));
    }

    /** Joueur visé : un joueur en ligne par son nom, ou n'importe quel joueur par son UUID (jamais de recherche Mojang). */
    private UUID target(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return online.getUniqueId();
        }
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String display(UUID uuid, String input) {
        Player online = Bukkit.getPlayer(uuid);
        return online != null ? online.getName() : input;
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(plugin.getMessage("usage-info"));
            return;
        }
        UUID uuid = target(args[1]);
        if (uuid == null) {
            sender.sendMessage(plugin.getMessage("player-not-found", "%player%", args[1]));
            return;
        }
        String name = display(uuid, args[1]);
        Map<String, Long> active = plugin.getCooldownManager().getActive(uuid);
        if (active.isEmpty()) {
            sender.sendMessage(plugin.getMessage("info-none", "%player%", name));
            return;
        }
        sender.sendMessage(plugin.getMessage("info-header", "%player%", name, "%count%", String.valueOf(active.size())));
        long now = System.currentTimeMillis();
        active.forEach((id, expiry) -> sender.sendMessage(plugin.getMessage("info-entry", "%id%", id,
            "%time%", FormatUtil.formatDuration(expiry - now))));
    }

    private void reset(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(plugin.getMessage("usage-reset"));
            return;
        }
        UUID uuid = target(args[1]);
        if (uuid == null) {
            sender.sendMessage(plugin.getMessage("player-not-found", "%player%", args[1]));
            return;
        }
        String id = args.length == 3 && !args[2].equalsIgnoreCase("all") ? args[2].toLowerCase(Locale.ROOT) : null;
        if (id != null && !plugin.getConfigManager().getGroupIds().contains(id)) {
            sender.sendMessage(plugin.getMessage("unknown-id", "%id%", args[2]));
            return;
        }
        String name = display(uuid, args[1]);
        int removed = plugin.getCooldownManager().clear(uuid, id);
        sender.sendMessage(removed == 0
            ? plugin.getMessage("reset-none", "%player%", name)
            : plugin.getMessage("reset-ok", "%player%", name, "%count%", String.valueOf(removed)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> allowed = allowed(sender);
        List<String> suggestions = new ArrayList<>();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            suggestions.addAll(allowed);
        } else if (args.length == 2 && (sub.equals("info") || sub.equals("reset")) && allowed.contains(sub)) {
            Bukkit.getOnlinePlayers().forEach(player -> suggestions.add(player.getName()));
        } else if (args.length == 3 && sub.equals("reset") && allowed.contains(sub)) {
            suggestions.add("all");
            suggestions.addAll(plugin.getConfigManager().getGroupIds());
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        return suggestions.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
    }
}
