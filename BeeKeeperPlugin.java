package com.kodari.beekeeper;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class BeeKeeperPlugin extends JavaPlugin {
    private HiveManager hiveManager;
    private BeeKeeperItems items;
    private BeeKeeperMenu menu;
    private FileConfiguration messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        hiveManager = new HiveManager(this);
        hiveManager.load();
        items = new BeeKeeperItems(this);
        menu = new BeeKeeperMenu(this, hiveManager);
        Bukkit.getPluginManager().registerEvents(menu, this);
        Bukkit.getPluginManager().registerEvents(new BeeKeeperListener(this, hiveManager, items), this);
        getCommand("beekeeper").setExecutor(new BeeKeeperCommand(this, hiveManager, items, menu));
    }

    @Override
    public void onDisable() {
        if (hiveManager != null) {
            hiveManager.save();
        }
    }

    public HiveManager getHiveManager() {
        return hiveManager;
    }

    public BeeKeeperItems getItems() {
        return items;
    }

    public String text(String path, String... replacements) {
        String value = messages.getString(path, path);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            value = value.replace(replacements[i], replacements[i + 1]);
        }
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    public List<String> textList(String path, String... replacements) {
        List<String> lines = new ArrayList<>();
        for (String value : messages.getStringList(path)) {
            for (int i = 0; i + 1 < replacements.length; i += 2) {
                value = value.replace(replacements[i], replacements[i + 1]);
            }
            lines.add(ChatColor.translateAlternateColorCodes('&', value));
        }
        return lines;
    }
}