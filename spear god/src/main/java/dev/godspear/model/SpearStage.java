package dev.godspear.model;

import org.bukkit.Material;

public enum SpearStage {
    WOOD("WOODEN_SPEAR", "TRIDENT"), STONE("STONE_SPEAR", "TRIDENT"), IRON("IRON_SPEAR", "TRIDENT"),
    DIAMOND("DIAMOND_SPEAR", "TRIDENT"), NETHERITE("NETHERITE_SPEAR", "TRIDENT"), GOD("NETHERITE_SPEAR", "TRIDENT");
    private final String preferred, fallback;
    SpearStage(String preferred, String fallback) { this.preferred = preferred; this.fallback = fallback; }
    public Material material() {
        Material m = Material.matchMaterial(preferred);
        return m != null && m.isItem() ? m : Material.valueOf(fallback);
    }
    public SpearStage next() { return this == GOD ? GOD : values()[ordinal() + 1]; }
    public static SpearStage parse(String s) { return valueOf(s.equalsIgnoreCase("wooden") ? "WOOD" : s.toUpperCase()); }
}
