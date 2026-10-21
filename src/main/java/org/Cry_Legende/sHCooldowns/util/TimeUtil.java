package org.Cry_Legende.sHCooldowns.util;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtil {

    // Pattern pour extraire les nombres et les unités (j, h, m, s) - insensible à la casse
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)\\s*([jhdms])", Pattern.CASE_INSENSITIVE);

    /**
     * Convertit une chaîne de temps comme "1h30m", "2j", "10s" en millisecondes.
     *
     * @param timeString La chaîne de temps.
     * @return Le temps total en millisecondes, ou -1 si le format est invalide.
     */
    public static long parseTime(String timeString) {
        if (timeString == null || timeString.isEmpty()) {
            return -1;
        }

        long totalMillis = 0;
        Matcher matcher = TIME_PATTERN.matcher(timeString);
        boolean found = false;

        while (matcher.find()) {
            found = true;
            try {
                long value = Long.parseLong(matcher.group(1));
                String unit = matcher.group(2).toLowerCase();

                switch (unit) {
                    case "j":
                    case "d": // Jour / Day
                        totalMillis += TimeUnit.DAYS.toMillis(value);
                        break;
                    case "h": // Heure / Hour
                        totalMillis += TimeUnit.HOURS.toMillis(value);
                        break;
                    case "m": // Minute
                        totalMillis += TimeUnit.MINUTES.toMillis(value);
                        break;
                    case "s": // Seconde / Second
                        totalMillis += TimeUnit.SECONDS.toMillis(value);
                        break;
                    default:
                        // Ne devrait pas arriver avec le regex, mais sécurité
                        return -1;
                }
            } catch (NumberFormatException e) {
                // Si la valeur numérique est trop grande
                return -1;
            }
        }

        return found ? totalMillis : -1; // Retourne -1 si aucun format valide n'est trouvé
    }
}
