package com.kodari.beekeeper;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BeeKeeperMenu implements Listener {
    private static final int[] UPGRADE_SLOTS = {10, 12, 14, 16, 22};
    private static final String[] UPGRADE_IDS = {"expand", "reservoir", "queen", "filter", "collector"};

    private final BeeKeeperPlugin plugin;
    private final HiveManager hives;

    public BeeKeeperMenu(BeeKeeperPlugin plugin, HiveManager hives) {
        this.plugin = plugin;
        this.hives = hives;
    }

    public void open(Player player, int page) {
        int selectedPage = Math.max(0, Math.min(2, page));
        MenuHolder holder = new MenuHolder(selectedPage);
        String title = selectedPage == 0 ? plugin.text("menu.title.overview") : selectedPage == 1 ? plugin.text("menu.title.bees") : plugin.text("menu.title.upgrades");
        Inventory inventory = Bukkit.createInventory(holder, 54, color(title));
        holder.setInventory(inventory);
        fillBackground(inventory);

        if (selectedPage == 0) {
            showOverview(player, inventory);
        } else if (selectedPage == 1) {
            showBees(inventory);
        } else {
            showUpgrades(player, inventory);
        }
        showNavigation(inventory, selectedPage);
        player.openInventory(inventory);
    }

    private void showOverview(Player player, Inventory inventory) {
        HiveData hive = ownedTargetedHive(player);
        int farmerLevel = hives.getLevel(player.getUniqueId());
        int tier = hives.getTier(player.getUniqueId());
        String radiusKey = tier == 3 ? "pollination.tier-three-radius" : tier == 2 ? "pollination.tier-two-radius" : "pollination.tier-one-radius";
        int radius = plugin.getConfig().getInt(radiusKey, tier == 3 ? 12 : tier == 2 ? 8 : 5);
        if (hive != null) {
            radius = hives.getRadius(hive);
        }

        set(inventory, 10, configuredItem("BEEHIVE", "menu.overview.hive.name", hive == null ? "menu.overview.hive.empty" : "menu.overview.hive.state",
                "{bee}", hive == null ? "" : beeName(hive.getBeeType()), "{nectar}", hive == null ? "" : String.valueOf(hive.getNectar()),
                "{max}", String.valueOf(plugin.getConfig().getInt("pollination.nectar-max", 100)), "{radius}", String.valueOf(radius),
                "{tier}", String.valueOf(tier), "{upgrades}", hive == null ? "" : upgradeCount(hive)));
        set(inventory, 12, configuredItem("WHEAT", "menu.overview.farmer.name", "menu.overview.farmer.lore", "{level}", String.valueOf(farmerLevel)));
        set(inventory, 14, configuredItem("GOLDEN_CARROT", "menu.overview.pollination.name", "menu.overview.pollination.lore"));
        set(inventory, 16, configuredItem("HONEY_BOTTLE", "menu.overview.nectar.name", "menu.overview.nectar.lore"));
        set(inventory, 20, configuredItem("POPPY", "menu.overview.flowers.name", "menu.overview.flowers.lore"));
        set(inventory, 22, configuredItem("HONEYCOMB", "menu.overview.products.name", "menu.overview.products.lore"));
        set(inventory, 24, configuredItem("BOOK", "menu.overview.usage.name", "menu.overview.usage.lore"));
        set(inventory, 29, configuredItem("BEE_NEST", "menu.overview.bees.name", "menu.overview.bees.lore"));
        set(inventory, 33, configuredItem("ANVIL", "menu.overview.upgrades.name", "menu.overview.upgrades.lore"));
    }

    private void showBees(Inventory inventory) {
        set(inventory, 10, configuredItem("BEEHIVE", "menu.bees.common.name", "menu.bees.common.lore"));
        set(inventory, 12, configuredItem("GOLD_INGOT", "menu.bees.golden.name", "menu.bees.golden.lore"));
        set(inventory, 14, configuredItem("DIAMOND", "menu.bees.crystal.name", "menu.bees.crystal.lore"));
        set(inventory, 16, configuredItem("NETHER_STAR", "menu.bees.royal.name", "menu.bees.royal.lore"));
        set(inventory, 22, configuredItem("OAK_LEAVES", "menu.bees.forest.name", "menu.bees.forest.lore"));
        set(inventory, 31, configuredItem("PAPER", "menu.bees.guide.name", "menu.bees.guide.lore"));
    }

    private void showUpgrades(Player player, Inventory inventory) {
        HiveData hive = ownedTargetedHive(player);
        int farmerLevel = hives.getLevel(player.getUniqueId());
        String[] icons = {"GOLD_INGOT", "WATER_BUCKET", "BEE_NEST", "HONEYCOMB", "HOPPER"};
        for (int index = 0; index < UPGRADE_IDS.length; index++) {
            String id = UPGRADE_IDS[index];
            BeeKeeperCommand.Upgrade upgrade = BeeKeeperCommand.UPGRADES.get(id);
            int current = hive == null ? 0 : hive.getUpgrades().getOrDefault(upgrade.key, 0);
            List<String> lore = new ArrayList<>(Arrays.asList(
                    plugin.text("menu.upgrades.lines.effect", "{effect}", plugin.text("menu.upgrades.effects." + id)),
                    plugin.text("menu.upgrades.lines.level", "{level}", String.valueOf(current), "{max}", String.valueOf(upgrade.maxLevel)),
                    plugin.text("menu.upgrades.lines.cost", "{cost}", String.valueOf(upgrade.cost)),
                    plugin.text("menu.upgrades.lines.required-level", "{level}", String.valueOf(upgrade.requiredLevel))
            ));
            lore.add(plugin.text(hive == null ? "menu.upgrades.lines.no-hive" : farmerLevel < upgrade.requiredLevel ? "menu.upgrades.lines.level-low" : current >= upgrade.maxLevel ? "menu.upgrades.lines.maxed" : "menu.upgrades.lines.purchase"));
            set(inventory, UPGRADE_SLOTS[index], item(icons[index], plugin.text("menu.upgrades.names." + id), lore.toArray(new String[0])));
        }
        set(inventory, 31, configuredItem("GOLDEN_CARROT", "menu.upgrades.hive.name", hive == null ? "menu.upgrades.hive.empty" : "menu.upgrades.hive.state",
                "{nectar}", hive == null ? "" : String.valueOf(hive.getNectar()), "{max}", String.valueOf(plugin.getConfig().getInt("pollination.nectar-max", 100)),
                "{bee}", hive == null ? "" : beeName(hive.getBeeType()), "{radius}", hive == null ? "" : String.valueOf(hives.getRadius(hive))));
    }

    private void showNavigation(Inventory inventory, int page) {
        String left = plugin.text(page == 1 ? "menu.navigation.overview" : "menu.navigation.bees");
        String right = plugin.text(page == 2 ? "menu.navigation.overview" : "menu.navigation.upgrades");
        set(inventory, 45, item("ARROW", left, plugin.text("menu.navigation.open")));
        set(inventory, 49, item("BARRIER", plugin.text("menu.navigation.close")));
        set(inventory, 53, item("ARROW", right, plugin.text("menu.navigation.open")));
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player) || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        MenuHolder holder = (MenuHolder) event.getView().getTopInventory().getHolder();
        int slot = event.getRawSlot();
        if (holder.page == 0 && slot == 29) {
            open(player, 1);
            return;
        }
        if (holder.page == 0 && slot == 33) {
            open(player, 2);
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        if (slot == 45) {
            open(player, holder.page == 0 ? 1 : holder.page == 1 ? 0 : 1);
            return;
        }
        if (slot == 53) {
            open(player, holder.page == 2 ? 0 : 2);
            return;
        }
        if (holder.page == 2) {
            for (int index = 0; index < UPGRADE_SLOTS.length; index++) {
                if (slot == UPGRADE_SLOTS[index]) {
                    player.performCommand("beekeeper upgrade " + UPGRADE_IDS[index]);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            open(player, 2);
                        }
                    });
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    private HiveData ownedTargetedHive(Player player) {
        Block target = player.getTargetBlockExact(6);
        if (target == null) {
            return null;
        }
        HiveData hive = hives.getHive(target);
        return hive != null && hive.getOwner().equals(player.getUniqueId()) ? hive : null;
    }

    private String upgradeCount(HiveData hive) {
        int count = 0;
        for (int level : hive.getUpgrades().values()) {
            count += level;
        }
        return String.valueOf(count);
    }

    private String beeName(String type) {
        switch (type) {
            case "GOLDEN": return plugin.text("bee-names.golden");
            case "CRYSTAL": return plugin.text("bee-names.crystal");
            case "ROYAL": return plugin.text("bee-names.royal");
            case "FOREST": return plugin.text("bee-names.forest");
            default: return plugin.text("bee-names.common");
        }
    }

    private void fillBackground(Inventory inventory) {
        ItemStack pane = item("BLACK_STAINED_GLASS_PANE", " ");
        if (pane == null) {
            return;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane);
        }
    }

    private void set(Inventory inventory, int slot, ItemStack item) {
        if (item != null) {
            inventory.setItem(slot, item);
        }
    }

    private ItemStack item(String material, String name, String... lore) {
        ItemStack item = XMaterial.matchXMaterial(material).map(XMaterial::parseItem).orElse(null);
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(color(name));
        List<String> formattedLore = new ArrayList<>();
        for (String line : lore) {
            formattedLore.add(color(line));
        }
        meta.setLore(formattedLore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack configuredItem(String material, String nameKey, String loreKey, String... replacements) {
        return item(material, plugin.text(nameKey), plugin.textList(loreKey, replacements).toArray(new String[0]));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static class MenuHolder implements InventoryHolder {
        private final int page;
        private Inventory inventory;

        private MenuHolder(int page) {
            this.page = page;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}