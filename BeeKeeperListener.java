package com.kodari.beekeeper;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Beehive;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class BeeKeeperListener implements Listener {
    private final BeeKeeperPlugin plugin;
    private final HiveManager hives;
    private final BeeKeeperItems items;
    private final Map<String, FlowerProfile> flowers = new HashMap<>();

    public BeeKeeperListener(BeeKeeperPlugin plugin, HiveManager hives, BeeKeeperItems items) {
        this.plugin = plugin;
        this.hives = hives;
        this.items = items;
        Bukkit.getScheduler().runTaskTimer(plugin, this::restoreNectar, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::autoCollect, 200L, 200L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateBeeNames, 100L, 100L);
    }

    private void updateBeeNames() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Bee bee : world.getEntitiesByClass(Bee.class)) {
                Location hiveLocation = bee.getHive();
                if (hiveLocation == null) {
                    continue;
                }
                HiveData hive = hives.getHive(hiveLocation.getBlock());
                String currentName = bee.getCustomName();
                if (hive == null) {
                    if (isBeeTypeName(currentName)) {
                        bee.setCustomName(null);
                        bee.setCustomNameVisible(false);
                    }
                    continue;
                }
                if (currentName != null && !isBeeTypeName(currentName)) {
                    continue;
                }
                bee.setCustomName(plugin.text("bee-entity-names." + beeTypeKey(hive.getBeeType())));
                bee.setCustomNameVisible(true);
            }
        }
    }

    private boolean isBeeTypeName(String name) {
        return name != null && (name.equals(plugin.text("bee-entity-names.common"))
                || name.equals(plugin.text("bee-entity-names.golden"))
                || name.equals(plugin.text("bee-entity-names.crystal"))
                || name.equals(plugin.text("bee-entity-names.royal"))
                || name.equals(plugin.text("bee-entity-names.forest")));
    }

    private String beeTypeName(String type) {
        return plugin.text("bee-names." + beeTypeKey(type));
    }

    private String beeTypeKey(String type) {
        switch (type) {
            case "GOLDEN": return "golden";
            case "CRYSTAL": return "crystal";
            case "ROYAL": return "royal";
            case "FOREST": return "forest";
            default: return "common";
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHiveInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand().name().equals("OFF_HAND")) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !HiveManager.isBeehive(block) || !event.getAction().name().startsWith("RIGHT_CLICK")) {
            return;
        }
        Player player = event.getPlayer();
        HiveData hive = hives.getHive(block);
        if (hive == null) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (player.isSneaking() && (held == null || held.getType().name().equals("AIR"))) {
                int limit = plugin.getConfig().getInt("claim.max-hives-per-player", 5);
                if (hives.getOwnedHiveCount(player.getUniqueId()) >= limit) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.text("messages.hive-limit", "{limit}", String.valueOf(limit)));
                    return;
                }
                event.setCancelled(true);
                hives.createHive(block, player.getUniqueId());
                hives.save();
                player.sendMessage(plugin.text("messages.hive-registered", "{level}", String.valueOf(hives.getLevel(player.getUniqueId()))));
            }
            return;
        }
        event.setCancelled(true);
        if (!hive.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.text("messages.hive-not-owner"));
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        String customId = items.identify(held);
        if (customId != null && (customId.startsWith("bee_") || customId.equals("pollination_elixir"))) {
            useSpecialItem(player, hive, held, customId);
            return;
        }

        String material = held == null ? "AIR" : held.getType().name();
        if (material.equals("GLASS_BOTTLE") || material.equals("SHEARS")) {
            harvestHive(player, block, hive, material.equals("GLASS_BOTTLE"));
        } else if (material.equals("AIR")) {
            player.sendMessage(plugin.text("messages.hive-status", "{nectar}", String.valueOf(hive.getNectar()),
                    "{max}", String.valueOf(plugin.getConfig().getInt("pollination.nectar-max", 100)), "{bee}", beeTypeName(hive.getBeeType())));
        }
    }

    private void useSpecialItem(Player player, HiveData hive, ItemStack held, String id) {
        if (id.equals("pollination_elixir")) {
            consumeMainHand(player, held);
            hive.setBoostUntil(System.currentTimeMillis() + 300_000L);
            hives.save();
            player.sendMessage(plugin.text("messages.elixir-active"));
            return;
        }
        int level = hives.getLevel(player.getUniqueId());
        String species;
        int required;
        switch (id) {
            case "bee_golden": species = "GOLDEN"; required = 21; break;
            case "bee_crystal": species = "CRYSTAL"; required = 20; break;
            case "bee_royal": species = "ROYAL"; required = 35; break;
            case "bee_forest": species = "FOREST"; required = 1; break;
            default: return;
        }
        if (level < required) {
            player.sendMessage(plugin.text("messages.bee-level-required", "{level}", String.valueOf(required)));
            return;
        }
        if (species.equals("ROYAL") && hive.getBeeType().equals("ROYAL")) {
            player.sendMessage(plugin.text("messages.bee-already-royal"));
            return;
        }
        hive.setBeeType(species);
        consumeMainHand(player, held);
        hives.save();
        player.sendMessage(plugin.text("messages.bee-installed", "{bee}", beeTypeName(species)));
    }

    private void harvestHive(Player player, Block block, HiveData hive, boolean bottle) {
        BlockData blockData = block.getBlockData();
        if (!(blockData instanceof Beehive)) {
            return;
        }
        Beehive beehive = (Beehive) blockData;
        if (beehive.getHoneyLevel() < 5) {
            player.sendMessage(plugin.text("messages.hive-not-ready"));
            return;
        }
        beehive.setHoneyLevel(0);
        block.setBlockData(beehive);
        if (bottle) {
            consumeMainHand(player, player.getInventory().getItemInMainHand());
            give(player, XMaterial.matchXMaterial("GLASS_BOTTLE").map(XMaterial::parseItem).orElse(null));
            give(player, items.create("pure_honey"));
            player.sendMessage(plugin.text("messages.honey-collected"));
        } else {
            give(player, XMaterial.matchXMaterial("HONEYCOMB").map(XMaterial::parseItem).orElse(null));
            int tier = hives.getTier(player.getUniqueId());
            double chance = tier == 3 ? 15.0 : tier == 2 ? 10.0 : 5.0;
            if (ThreadLocalRandom.current().nextDouble(100.0) < chance) {
                give(player, items.create("golden_comb"));
                player.sendMessage(plugin.text("messages.golden-comb-found"));
            }
        }
        trySpecialBeeDrop(player, block, hive);
        hives.save();
    }

    private void trySpecialBeeDrop(Player player, Block block, HiveData hive) {
        int level = hives.getLevel(player.getUniqueId());
        boolean hasQueenChamber = hive.getUpgrades().getOrDefault("queen_chamber", 0) > 0;

        if (hasQueenChamber && level >= 35 && hive.getBeeType().equals("COMMON") && roll(2.0)) {
            String beeItem = ThreadLocalRandom.current().nextBoolean() ? "bee_golden" : "bee_crystal";
            give(player, items.create(beeItem));
            player.sendMessage(plugin.text("messages.mutation-found", "{bee}", plugin.text("bee-names." + beeItem.substring(4))));
        }

        if (hasQueenChamber && level >= 35 && !hive.getBeeType().equals("ROYAL") && roll(1.0)) {
            give(player, items.create("bee_royal"));
            player.sendMessage(plugin.text("messages.royal-found"));
        }

        if (roll(5.0)) {
            give(player, items.create("bee_forest"));
            player.sendMessage(plugin.text("messages.forest-found"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCropGrow(BlockGrowEvent event) {
        HiveData hive = hives.findNearestPollinatingHive(event.getBlock().getLocation());
        if (hive == null) {
            return;
        }
        if (consumeNectar(hive) && event.getNewState().getBlockData() instanceof Ageable) {
            double chance = growthChance(hive, event.getBlock().getType().name());
            if (roll(chance)) {
                BlockState newState = event.getNewState();
                Ageable ageable = (Ageable) newState.getBlockData();
                ageable.setAge(Math.min(ageable.getAge() + 1, ageable.getMaximumAge()));
                newState.setBlockData(ageable);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCropBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (HiveManager.isBeehive(block)) {
            HiveData hive = hives.getHive(block);
            if (hive != null && !hive.getOwner().equals(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(plugin.text("messages.hive-break-denied"));
            } else if (hive != null) {
                hives.removeHive(hive);
                hives.save();
            }
            return;
        }
        if (!isMatureCrop(block)) {
            return;
        }
        HiveData hive = hives.findNearestPollinatingHive(block.getLocation());
        if (hive == null || !consumeNectar(hive)) {
            return;
        }
        String drop = cropDrop(block.getType().name());
        if (drop == null) {
            return;
        }
        double chance = harvestChance(hive, block.getType().name());
        if (!roll(chance)) {
            return;
        }
        XMaterial.matchXMaterial(drop).map(XMaterial::parseItem).ifPresent(item -> block.getWorld().dropItemNaturally(block.getLocation(), item));
    }

    private boolean isMatureCrop(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Ageable) {
            Ageable ageable = (Ageable) data;
            return ageable.getAge() >= ageable.getMaximumAge();
        }
        String name = block.getType().name();
        return name.equals("PUMPKIN") || name.equals("MELON");
    }

    private String cropDrop(String name) {
        switch (name) {
            case "WHEAT": return "WHEAT";
            case "CARROTS": return "CARROT";
            case "POTATOES": return "POTATO";
            case "BEETROOTS": return "BEETROOT";
            case "PUMPKIN": return "PUMPKIN";
            case "MELON": return "MELON_SLICE";
            case "SWEET_BERRY_BUSH": return "SWEET_BERRIES";
            default: return null;
        }
    }

    private double growthChance(HiveData hive, String cropName) {
        int tier = hives.getTier(hive.getOwner());
        double chance = tier == 3
                ? plugin.getConfig().getDouble("pollination.tier-three-growth-bonus", 30)
                : tier == 2 ? plugin.getConfig().getDouble("pollination.tier-two-growth-bonus", 20)
                : plugin.getConfig().getDouble("pollination.tier-one-growth-bonus", 10);
        if (hive.getBeeType().equals("CRYSTAL")) {
            chance += 10;
        } else if (hive.getBeeType().equals("ROYAL")) {
            chance += 5;
        }
        if (hive.getBeeType().equals("FOREST") && cropName.equals("SWEET_BERRY_BUSH")) {
            chance += 15;
        }
        if (isBoosted(hive)) {
            chance += 25;
        }
        return Math.min(100, chance);
    }

    private double harvestChance(HiveData hive, String cropName) {
        int tier = hives.getTier(hive.getOwner());
        double chance = tier == 3
                ? plugin.getConfig().getDouble("pollination.tier-three-harvest-bonus", 10)
                : tier == 2 ? plugin.getConfig().getDouble("pollination.tier-two-harvest-bonus", 5)
                : plugin.getConfig().getDouble("pollination.tier-one-harvest-bonus", 2);
        chance += hive.getUpgrades().getOrDefault("pollen_filter", 0) * 5.0;
        if (hive.getBeeType().equals("GOLDEN") || hive.getBeeType().equals("ROYAL")) {
            chance += 10;
        }
        if (hive.getBeeType().equals("FOREST") && (cropName.equals("PUMPKIN") || cropName.equals("MELON") || cropName.equals("SWEET_BERRY_BUSH"))) {
            chance += 10;
        }
        FlowerProfile profile = flowers.get(hive.key());
        if (profile != null) {
            if (cropName.equals("WHEAT")) {
                chance += Math.min(5, profile.cornflowers);
            }
            if (cropName.equals("PUMPKIN") || cropName.equals("MELON")) {
                chance += profile.sunflowers > 0 ? 5 : 0;
            }
            chance += profile.lilacs > 0 ? 5 : 0;
        }
        if (isBoosted(hive)) {
            chance += 10;
        }
        return Math.min(75, chance);
    }

    private boolean consumeNectar(HiveData hive) {
        if (hive.getNectar() <= 0) {
            return false;
        }
        if (hive.getUpgrades().getOrDefault("nectar_reservoir", 0) <= 0 || ThreadLocalRandom.current().nextDouble() >= 0.25) {
            hive.setNectar(hive.getNectar() - 1);
        }
        return true;
    }

    private boolean roll(double chance) {
        return ThreadLocalRandom.current().nextDouble(100.0) < chance;
    }

    private boolean isBoosted(HiveData hive) {
        return hive.getBoostUntil() > System.currentTimeMillis();
    }

    private void restoreNectar() {
        flowers.clear();
        int maximum = plugin.getConfig().getInt("pollination.nectar-max", 100);
        for (HiveData hive : hives.getHives()) {
            org.bukkit.World world = Bukkit.getWorld(hive.getWorld());
            if (world == null) {
                continue;
            }
            int radius = hives.getRadius(hive);
            int poppies = 0;
            int cornflowers = 0;
            int sunflowers = 0;
            int lilacs = 0;
            int total = 0;
            for (int x = hive.getX() - radius; x <= hive.getX() + radius; x++) {
                for (int z = hive.getZ() - radius; z <= hive.getZ() + radius; z++) {
                    for (int y = hive.getY() - 2; y <= hive.getY() + 3; y++) {
                        String name = world.getBlockAt(x, y, z).getType().name();
                        if (!isFlower(name)) {
                            continue;
                        }
                        total++;
                        if (name.equals("POPPY")) poppies++;
                        if (name.equals("CORNFLOWER")) cornflowers++;
                        if (name.equals("SUNFLOWER")) sunflowers++;
                        if (name.equals("LILAC")) lilacs++;
                    }
                }
            }
            flowers.put(hive.key(), new FlowerProfile(cornflowers, sunflowers, lilacs));
            if (total > 0) {
                int recovery = Math.max(1, total / 5) + poppies / 5;
                hive.setNectar(Math.min(maximum, hive.getNectar() + recovery));
            }
        }
        hives.save();
    }

    private boolean isFlower(String name) {
        return name.equals("POPPY") || name.equals("DANDELION") || name.equals("CORNFLOWER") || name.equals("SUNFLOWER") ||
                name.equals("LILAC") || name.equals("ROSE_BUSH") || name.equals("PEONY") || name.equals("AZURE_BLUET") ||
                name.equals("OXEYE_DAISY") || name.equals("ALLIUM") || name.endsWith("_TULIP") || name.equals("LILY_OF_THE_VALLEY") ||
                name.equals("BLUE_ORCHID") || name.equals("TORCHFLOWER") || name.equals("PITCHER_PLANT");
    }

    private void autoCollect() {
        for (HiveData hive : hives.getHives()) {
            if (hive.getUpgrades().getOrDefault("auto_collector", 0) <= 0) {
                continue;
            }
            org.bukkit.World world = Bukkit.getWorld(hive.getWorld());
            Player owner = Bukkit.getPlayer(hive.getOwner());
            if (world == null || owner == null) {
                continue;
            }
            Block block = world.getBlockAt(hive.getX(), hive.getY(), hive.getZ());
            if (!(block.getBlockData() instanceof Beehive)) {
                continue;
            }
            Beehive beehive = (Beehive) block.getBlockData();
            if (beehive.getHoneyLevel() < 5) {
                continue;
            }
            beehive.setHoneyLevel(0);
            block.setBlockData(beehive);
            give(owner, items.create("pure_honey"));
            trySpecialBeeDrop(owner, block, hive);
            hives.save();
        }
    }

    private void consumeMainHand(Player player, ItemStack held) {
        if (held == null || held.getType().name().equals("AIR")) {
            return;
        }
        if (held.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            held.setAmount(held.getAmount() - 1);
        }
    }

    private void give(Player player, ItemStack item) {
        if (item == null) {
            return;
        }
        for (ItemStack drop : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }

    private static class FlowerProfile {
        private final int cornflowers;
        private final int sunflowers;
        private final int lilacs;

        private FlowerProfile(int cornflowers, int sunflowers, int lilacs) {
            this.cornflowers = cornflowers;
            this.sunflowers = sunflowers;
            this.lilacs = lilacs;
        }
    }
}