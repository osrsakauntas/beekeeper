package com.kodari.beekeeper;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class BeeKeeperCommand implements CommandExecutor {
    static final Map<String, Upgrade> UPGRADES = new HashMap<>();

    static {
        UPGRADES.put("expand", new Upgrade("expanded_hive", 3, 3, 11));
        UPGRADES.put("reservoir", new Upgrade("nectar_reservoir", 1, 5, 11));
        UPGRADES.put("queen", new Upgrade("queen_chamber", 1, 8, 35));
        UPGRADES.put("filter", new Upgrade("pollen_filter", 3, 4, 20));
        UPGRADES.put("collector", new Upgrade("auto_collector", 1, 10, 26));
    }

    private final BeeKeeperPlugin plugin;
    private final HiveManager hives;
    private final BeeKeeperItems items;
    private final BeeKeeperMenu menu;

    public BeeKeeperCommand(BeeKeeperPlugin plugin, HiveManager hives, BeeKeeperItems items, BeeKeeperMenu menu) {
        this.plugin = plugin;
        this.hives = hives;
        this.items = items;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("give")) {
            return giveItem(sender, args);
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.text("commands.player-only"));
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0 || args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("guide")) {
            menu.open(player, 0);
            return true;
        }
        if (args[0].equalsIgnoreCase("upgrade")) {
            if (args.length < 2) {
                player.sendMessage(plugin.text("commands.upgrade-usage"));
                return true;
            }
            upgrade(player, args[1].toLowerCase());
            return true;
        }
        player.sendMessage(plugin.text("commands.usage"));
        return true;
    }

    private boolean giveItem(CommandSender sender, String[] args) {
        if (!sender.hasPermission("beekeeper.admin")) {
            sender.sendMessage(plugin.text("commands.no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.text("commands.give-usage"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.text("commands.player-offline"));
            return true;
        }
        String id = args[2].toLowerCase();
        if (!items.isSupported(id)) {
            sender.sendMessage(plugin.text("commands.unknown-item"));
            return true;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException exception) {
                sender.sendMessage(plugin.text("commands.invalid-amount"));
                return true;
            }
        }
        ItemStack item = items.create(id);
        if (item == null) {
            sender.sendMessage(plugin.text("commands.item-create-failed"));
            return true;
        }
        item.setAmount(amount);
        target.getInventory().addItem(item).values().forEach(drop -> target.getWorld().dropItemNaturally(target.getLocation(), drop));
        sender.sendMessage(plugin.text("commands.item-given", "{player}", target.getName()));
        return true;
    }

    private void upgrade(Player player, String name) {
        Block target = player.getTargetBlockExact(6);
        if (target == null) {
            player.sendMessage(plugin.text("commands.look-at-hive"));
            return;
        }
        HiveData hive = hives.getHive(target);
        if (hive == null || !hive.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.text("commands.not-your-hive"));
            return;
        }
        Upgrade upgrade = UPGRADES.get(name);
        if (upgrade == null) {
            player.sendMessage(plugin.text("commands.unknown-upgrade"));
            return;
        }
        int level = hives.getLevel(player.getUniqueId());
        if (level < upgrade.requiredLevel) {
            player.sendMessage(plugin.text("commands.upgrade-level-required", "{level}", String.valueOf(upgrade.requiredLevel)));
            return;
        }
        int current = hive.getUpgrades().getOrDefault(upgrade.key, 0);
        if (current >= upgrade.maxLevel) {
            player.sendMessage(plugin.text("commands.upgrade-max"));
            return;
        }
        if (!removeCustomItems(player, "golden_comb", upgrade.cost)) {
            player.sendMessage(plugin.text("commands.upgrade-cost", "{cost}", String.valueOf(upgrade.cost)));
            return;
        }
        hive.getUpgrades().put(upgrade.key, current + 1);
        hives.save();
        player.sendMessage(plugin.text("commands.upgrade-bought", "{upgrade}", plugin.text("menu.upgrades.names." + name), "{level}", String.valueOf(current + 1), "{max}", String.valueOf(upgrade.maxLevel)));
    }

    private boolean removeCustomItems(Player player, String id, int amount) {
        int found = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (id.equals(items.identify(item))) {
                found += item.getAmount();
            }
        }
        if (found < amount) {
            return false;
        }
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (!id.equals(items.identify(item))) {
                continue;
            }
            int removed = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - removed);
            if (item.getAmount() <= 0) {
                contents[slot] = null;
            }
            remaining -= removed;
        }
        player.getInventory().setStorageContents(contents);
        return true;
    }

    static class Upgrade {
        final String key;
        final int maxLevel;
        final int cost;
        final int requiredLevel;

        private Upgrade(String key, int maxLevel, int cost, int requiredLevel) {
            this.key = key;
            this.maxLevel = maxLevel;
            this.cost = cost;
            this.requiredLevel = requiredLevel;
        }
    }
}