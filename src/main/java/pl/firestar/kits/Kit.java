package pl.firestar.kits;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class Kit {

    private final String id;
    private String name;
    private ItemStack icon = new ItemStack(Material.CHEST);
    private final ItemStack[] items = new ItemStack[KitManager.CONTENT_SIZE];
    private int slot = -1;
    private long cooldown = 0L; // sekundy; 0 = brak, -1 = jednorazowy
    private String rank = "gracz";
    private boolean enabled = true;

    public Kit(String id) {
        this.id = id;
        this.name = "&e" + id;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ItemStack getIcon() { return icon; }
    public void setIcon(ItemStack icon) { this.icon = icon; }
    public ItemStack[] getItems() { return items; }
    public int getSlot() { return slot; }
    public void setSlot(int slot) { this.slot = slot; }
    public long getCooldown() { return cooldown; }
    public void setCooldown(long cooldown) { this.cooldown = cooldown; }
    public String getRank() { return rank; }
    public void setRank(String rank) { this.rank = rank; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int countItems() {
        int n = 0;
        for (ItemStack it : items) {
            if (it != null && it.getType() != Material.AIR) n++;
        }
        return n;
    }
}
