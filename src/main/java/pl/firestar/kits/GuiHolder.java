package pl.firestar.kits;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class GuiHolder implements InventoryHolder {

    public enum Type { MENU, PREVIEW, EDITOR, SLOTS, COOLDOWN, CONTENTS }

    private final Type type;
    private final String kitId;
    private Inventory inventory;

    public GuiHolder(Type type, String kitId) {
        this.type = type;
        this.kitId = kitId;
    }

    public Type getType() { return type; }
    public String getKitId() { return kitId; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
