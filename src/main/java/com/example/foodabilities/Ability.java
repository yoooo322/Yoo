package com.example.foodabilities;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Ability {
    private final String id;
    private final String displayName;
    private final List<String> loreLines;
    private final List<PotionEffect> potionEffects;

    public Ability(String id,
                   String displayName,
                   List<String> loreLines,
                   List<PotionEffect> potionEffects) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = displayName;
        this.loreLines = loreLines == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(loreLines));
        this.potionEffects = Collections.unmodifiableList(new ArrayList<>(potionEffects));
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getLoreLines() {
        return loreLines;
    }

    public List<PotionEffect> getPotionEffects() {
        return potionEffects;
    }

    @SuppressWarnings("unchecked")
    public static Ability fromSection(String id, ConfigurationSection section) {
        String name = section.getString("name", id);
        if (name != null) {
            name = ChatColor.translateAlternateColorCodes('&', name);
        }

        List<String> lore = new ArrayList<>();
        for (String line : section.getStringList("lore")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }

        int infiniteTicks = 1_000_000_000; // very long
        List<PotionEffect> effects = new ArrayList<>();

        List<Map<?, ?>> rawEffects = section.getMapList("effects");
        for (Map<?, ?> map : rawEffects) {
            Object typeObj = map.get("type");
            if (typeObj == null) continue;
            PotionEffectType type = PotionEffectType.getByName(String.valueOf(typeObj));
            if (type == null) continue;

            Object durationObj = map.get("duration");
            int duration;
            if (durationObj == null) {
                duration = infiniteTicks;
            } else if (durationObj instanceof Number) {
                duration = ((Number) durationObj).intValue();
            } else {
                String durStr = String.valueOf(durationObj);
                if ("infinite".equalsIgnoreCase(durStr) || "forever".equalsIgnoreCase(durStr)) {
                    duration = infiniteTicks;
                } else {
                    try {
                        duration = Integer.parseInt(durStr);
                    } catch (NumberFormatException e) {
                        duration = infiniteTicks;
                    }
                }
            }

            int amplifier = 0;
            Object ampObj = map.get("amplifier");
            if (ampObj instanceof Number) {
                amplifier = Math.max(0, ((Number) ampObj).intValue());
            } else if (ampObj != null) {
                try { amplifier = Math.max(0, Integer.parseInt(String.valueOf(ampObj))); } catch (NumberFormatException ignored) {}
            }

            boolean ambient = getBoolean(map.get("ambient"), true);
            boolean particles = getBoolean(map.get("particles"), false);
            boolean icon = getBoolean(map.get("icon"), true);

            effects.add(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
        }

        return new Ability(id, name, lore, effects);
    }

    private static boolean getBoolean(Object value, boolean def) {
        if (value == null) return def;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
