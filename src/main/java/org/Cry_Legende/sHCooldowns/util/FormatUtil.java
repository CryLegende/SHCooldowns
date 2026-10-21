package org.Cry_Legende.sHCooldowns.util;

import java.util.concurrent.TimeUnit;

public class FormatUtil {

    /**
     * Formate une durée en millisecondes en une chaîne lisible (ex: "1h 30m 5s").
     *
     * @param millis Durée en millisecondes.
     * @return Chaîne formatée.
     */
    public static String formatDuration(long millis) {
        if (millis < 0) {
            return "0s"; // Ou une autre indication d'erreur/temps invalide
        }

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("j ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0 || sb.length() == 0) { // Affiche les secondes si c'est la seule unité ou si le reste est 0
            sb.append(seconds).append("s");
        }

        // Supprimer l'espace final s'il y en a un
        return sb.toString().trim();
    }

    /**
     * Remplace les codes couleurs Bukkit (&) par le caractère de section (§).
     */
    public static String colorize(String message) {
        if (message == null) return null;
        return message.replace('&', '§');
    }
}
