package com.kodari.beekeeper;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HiveManager {
    private final BeeKeeperPlugin plugin;
    private final Map<String, HiveData> hives = new HashMap<>();
    private final FarmerLevelProvider farmerLevels;
    private final File file;

    public HiveManager(BeeKeeperPlugin plugin) {
        this.plugin = plugin;
        this.farmerLevels = new FarmerLevelProvider(plugin);
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection hiveSection = yaml.getConfigurationSection("hives");
        if (hiveSection != null) {
            for (String key : hiveSection.getKeys(false)) {
                ConfigurationSection section = hiveSection.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                String world = section.getString("world");
                String ownerValue = section.getString("owner");
                if (world == null || ownerValue == null) {
                    continue;
                }
                try {
                    HiveData hive = new HiveData(world, section.getInt("x"), section.getInt("y"),
                            section.getInt("z"), UUID.fromString(ownerValue));
                    hive.setNectar(section.getInt("nectar", 100));
                    hive.setBeeType(section.getString("bee-type", "COMMON"));
                    hive.setBoostUntil(section.getLong("boost-until", 0L));
                    ConfigurationSection upgrades = section.getConfigurationSection("upgrades");
                    if (upgrades != null) {
                        for (String upgrade : upgrades.getKeys(false)) {
                            hive.getUpgrades().put(upgrade, upgrades.getInt(upgrade));
                        }
                    }
                    hives.put(hive.key(), hive);
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("Praleidžiamas netinkamas bityno savininko UUID: " + key);
                }
            }
        }

    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (HiveData hive : hives.values()) {
            String path = "hives." + hive.key();
            yaml.set(path + ".world", hive.getWorld());
            yaml.set(path + ".x", hive.getX());
            yaml.set(path + ".y", hive.getY());
            yaml.set(path + ".z", hive.getZ());
            yaml.set(path + ".owner", hive.getOwner().toString());
            yaml.set(path + ".nectar", hive.getNectar());
            yaml.set(path + ".bee-type", hive.getBeeType());
            yaml.set(path + ".boost-until", hive.getBoostUntil());
            for (Map.Entry<String, Integer> upgrade : hive.getUpgrades().entrySet()) {
                yaml.set(path + ".upgrades." + upgrade.getKey(), upgrade.getValue());
            }
        }
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().severe("Nepavyko sukurti BeeKeeper duomenų aplanko.");
                return;
            }
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Nepavyko išsaugoti BeeKeeper duomenų: " + exception.getMessage());
        }
    }

    public HiveData getHive(Block block) {
        return hives.get(key(block.getLocation()));
    }

    public HiveData createHive(Block block, UUID owner) {
        HiveData hive = new HiveData(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), owner);
        hive.setNectar(plugin.getConfig().getInt("pollination.nectar-max", 100));
        hives.put(hive.key(), hive);
        return hive;
    }

    public void removeHive(HiveData hive) {
        hives.remove(hive.key());
    }

    public Collection<HiveData> getHives() {
        return new ArrayList<>(hives.values());
    }

    public int getOwnedHiveCount(UUID owner) {
        int count = 0;
        for (HiveData hive : hives.values()) {
            if (hive.getOwner().equals(owner)) {
                count++;
            }
        }
        return count;
    }

    public int getLevel(UUID player) {
        return farmerLevels.getLevel(player);
    }

    public int getTier(UUID player) {
        int level = getLevel(player);
        if (level >= plugin.getConfig().getInt("integration.royal-farmer-level", 26)) {
            return 3;
        }
        if (level >= plugin.getConfig().getInt("integration.professional-farmer-level", 11)) {
            return 2;
        }
        return 1;
    }

    public int getRadius(HiveData hive) {
        int tier = getTier(hive.getOwner());
        int radius;
        if (tier == 3) {
            radius = plugin.getConfig().getInt("pollination.tier-three-radius", 12);
        } else if (tier == 2) {
            radius = plugin.getConfig().getInt("pollination.tier-two-radius", 8);
        } else {
            radius = plugin.getConfig().getInt("pollination.tier-one-radius", 5);
        }
        return radius + hive.getUpgrades().getOrDefault("expanded_hive", 0) * 2;
    }

    public HiveData findNearestPollinatingHive(Location location) {
        HiveData nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (HiveData hive : hives.values()) {
            if (hive.getNectar() <= 0) {
                continue;
            }
            World world = Bukkit.getWorld(hive.getWorld());
            if (world == null || !world.getUID().equals(location.getWorld().getUID())) {
                continue;
            }
            Location center = new Location(world, hive.getX() + 0.5, hive.getY() + 0.5, hive.getZ() + 0.5);
            double distance = center.distanceSquared(location);
            int radius = getRadius(hive);
            if (distance <= (double) radius * radius && distance < nearestDistance) {
                nearest = hive;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    public static boolean isBeehive(Block block) {
        String name = block.getType().name();
        return name.equals("BEEHIVE") || name.equals("BEE_NEST");
    }

    private String key(Location location) {
        return location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }
}