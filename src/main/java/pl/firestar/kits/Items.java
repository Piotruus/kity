package pl.firestar.kits;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Items {

    private Items() {}

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    public static ItemStack make(Material material, String name, String... lore) {
        return make(material, name, Arrays.asList(lore));
    }

    public static ItemStack make(Material material, String name, List<String> lore) {
        return decorate(new ItemStack(material), name, lore);
    }

    /** Dodaje efekt blysku (ukryty enchant). */
    public static ItemStack glow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
        if (e != null) {
            meta.addEnchant(e, 1, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack decorate(ItemStack item, String name, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(color(name));
        List<String> colored = new ArrayList<>();
        for (String line : lore) colored.add(color(line));
        meta.setLore(colored);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }
}
