package com.kodari.beekeeper;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class HiveData {
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final UUID owner;
    private int nectar = 100;
    private String beeType = "COMMON";
    private long boostUntil;
    private final Map<String, Integer> upgrades = new HashMap<>();

    public HiveData(String world, int x, int y, int z, UUID owner) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.owner = owner;
    }

    public String key() {
        return world + "," + x + "," + y + "," + z;
    }
}