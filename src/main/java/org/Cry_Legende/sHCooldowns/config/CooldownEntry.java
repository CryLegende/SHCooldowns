package org.Cry_Legende.sHCooldowns.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CooldownEntry {
    private final String id; // ID unique du groupe (ex: "repair", "repair_all")
    private final String specificPermission; // Permission pour CETTE entrée (null si c'est la défaut)
    private final long cooldownMillis;
    private final List<String> sharedCommandPatterns; // Liste des motifs de commande (ex: "/repair", "/erepair *")

    public CooldownEntry(String id, String specificPermission, long cooldownMillis, List<String> sharedCommandPatterns) {
        this.id = id;
        this.specificPermission = specificPermission;
        this.cooldownMillis = cooldownMillis;
        // Crée une copie immuable pour la sécurité
        this.sharedCommandPatterns = Collections.unmodifiableList(new ArrayList<>(sharedCommandPatterns));
    }

    public String getId() {
        return id;
    }

    public String getSpecificPermission() {
        return specificPermission;
    }

    public long getCooldownMillis() {
        return cooldownMillis;
    }

    public List<String> getSharedCommandPatterns() {
        return sharedCommandPatterns;
    }

    /**
     * Vérifie si un motif de commande donné correspond à un des motifs de cette entrée.
     * Gère le cas '*' comme un wildcard pour les arguments.
     */
    public boolean matches(String commandLine) {
        String commandLower = commandLine.toLowerCase().trim();
        if (!commandLower.startsWith("/")) {
            commandLower = "/" + commandLower;
        }

        for (String pattern : sharedCommandPatterns) {
            String patternLower = pattern.toLowerCase().trim();
            if (patternLower.endsWith(" *")) {
                // Wildcard : vérifie si la commande commence par le motif avant " *"
                String prefix = patternLower.substring(0, patternLower.length() - 2); // Enlève " *"
                // Assure que le préfixe match exactement ou est suivi d'un espace
                if (commandLower.equals(prefix) || commandLower.startsWith(prefix + " ")) {
                    return true;
                }
            } else {
                // Correspondance exacte
                if (commandLower.equals(patternLower)) {
                    return true;
                }
                // Gérer aussi le cas où la commande a des args mais le pattern n'en a pas (ex: pattern "/repair", command "/repair abc")
                // Sauf si une règle plus spécifique existe pour "/repair abc"
                // Ce matching est délicat. Simplifions : un pattern SANS '*' doit matcher EXACTEMENT la commande.
                // Les patterns AVEC '*' matchent le début de la commande.
                // La logique de priorité gérera le choix de la meilleure règle.
            }
        }
        return false;
    }

    /**
     * Vérifie si le motif pattern correspond exactement à la commande donnée.
     * Spécifique pour PAPI pour trouver l'ID exact.
     */
    public boolean commandExactlyMatchesPattern(String commandLine) {
        String commandLower = commandLine.toLowerCase().trim();
        if (!commandLower.startsWith("/")) {
            commandLower = "/" + commandLower;
        }
        for (String pattern : sharedCommandPatterns) {
            if (commandLower.equals(pattern.toLowerCase().trim())) {
                return true;
            }
        }
        return false;
    }
}