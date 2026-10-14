package com.kodari.beekeeper;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.NamespacedKey;

public class BeeKeeperItems {
    private static final Map<String, String> MATERIALS = Map.of(
            "pure_honey", "HONEY_BOTTLE",
            "golden_comb", "HONEYCOMB",
            "pollination_elixir", "HONEY_BOTTLE",
            "bee_golden", "PAPER",
            "bee_crystal", "PAPER",
            "bee_royal", "PAPER",
            "bee_forest", "PAPER"
    );
    private final BeeKeeperPlugin plugin;
    private final NamespacedKey itemKey;

    public BeeKeeperItems(BeeKeeperPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "beekeeper_item");
    }

    public ItemStack create(String id) {
        String materialName = MATERIALS.get(id);
        if (materialName == null) {
            return null;
        }
        Optional<XMaterial> matched = XMaterial.matchXMaterial(materialName);
        ItemStack item = matched.map(XMaterial::parseItem).orElse(null);
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(plugin.text("items." + id + ".name"));
        meta.setLore(plugin.textList("items." + id + ".lore"));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    public String identify(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    public boolean isSupported(String id) {
        return MATERIALS.containsKey(id);
    }

}