package com.kodari.beekeeper;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FarmerLevelProvider {
    private final BeeKeeperPlugin plugin;
    private final Map<UUID, CachedLevel> cache = new HashMap<>();
    private boolean warned;

    public FarmerLevelProvider(BeeKeeperPlugin plugin) {
        this.plugin = plugin;
    }

    public int getLevel(UUID playerId) {
        long now = System.currentTimeMillis();
        CachedLevel cached = cache.get(playerId);
        if (cached != null && cached.expiresAt > now) {
            return cached.level;
        }

        int level = resolveLevel(playerId);
        cache.put(playerId, new CachedLevel(level, now + 1000L));
        return level;
    }

    private int resolveLevel(UUID playerId) {
        String placeholder = plugin.getConfig().getString("integration.farmer-level-placeholder", "%farmer_level%");
        if (placeholder == null || placeholder.trim().isEmpty() || !Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            warnUnavailable();
            return 1;
        }

        try {
            Class<?> placeholderApi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method method = placeholderApi.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            String result = (String) method.invoke(null, Bukkit.getOfflinePlayer(playerId), placeholder);
            return Math.max(1, Integer.parseInt(result.trim()));
        } catch (ReflectiveOperationException | NumberFormatException exception) {
            warnUnavailable();
            return 1;
        }
    }

    private void warnUnavailable() {
        if (warned) {
            return;
        }
        warned = true;
        plugin.getLogger().warning("Ūkininko lygio placeholder nepavyko nuskaityti; naudojamas 1 lygis. Įdiekite PlaceholderAPI ir nustatykite integration.farmer-level-placeholder.");
    }

    private static class CachedLevel {
        private final int level;
        private final long expiresAt;

        private CachedLevel(int level, long expiresAt) {
            this.level = level;
            this.expiresAt = expiresAt;
        }
    }
}