package pl.firestar.kits;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class GuiListener implements Listener {

    private static final String ADMIN = "firestarkits.admin";

    private final Main plugin;
    private final KitManager manager;
    private final GuiManager gui;

    public GuiListener(Main plugin, KitManager manager, GuiManager gui) {
        this.plugin = plugin;
        this.manager = manager;
        this.gui = gui;
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    private boolean isLeft(ClickType c) {
        return c == ClickType.LEFT || c == ClickType.SHIFT_LEFT;
    }

    private boolean isRight(ClickType c) {
        return c == ClickType.RIGHT || c == ClickType.SHIFT_RIGHT;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof GuiHolder)) return;
        if (!(e.getWhoClicked() instanceof Player)) return;

        final Player p = (Player) e.getWhoClicked();
        final GuiHolder gh = (GuiHolder) holder;
        final GuiHolder.Type type = gh.getType();
        final int raw = e.getRawSlot();
        final boolean inTop = raw >= 0 && raw < top.getSize();
        final ClickType click = e.getClick();
        final boolean admin = p.hasPermission(ADMIN);

        // ---- edytor zawartosci: gorne 45 slotow wolne, dolny rzad zablokowany
        if (type == GuiHolder.Type.CONTENTS) {
            if (!admin) {
                e.setCancelled(true);
                later(p::closeInventory);
                return;
            }
            if (inTop && raw >= KitManager.CONTENT_SIZE) {
                e.setCancelled(true);
                if (raw == GuiManager.BACK_SLOT) {
                    final String id = gh.getKitId();
                    later(() -> {
                        Kit k = manager.get(id);
                        if (k != null) gui.openEditor(p, k);
                    });
                }
            }
            return;
        }

        // ---- wszystkie pozostale okna sa tylko do klikania
        e.setCancelled(true);
        if (!inTop) return;

        if (type == GuiHolder.Type.MENU) {
            Kit k = manager.kitAt(raw);
            if (k == null) {
                if (raw == GuiManager.MENU_CLOSE) later(p::closeInventory);
                return;
            }
            if (!k.isEnabled() && !admin) return;
            if (click == ClickType.SHIFT_RIGHT && admin) {
                later(() -> gui.openEditor(p, k));
            } else if (isRight(click)) {
                later(() -> gui.openPreview(p, k));
            } else if (isLeft(click)) {
                KitManager.Status st = manager.claim(p, k);
                if (st == KitManager.Status.OK && plugin.getConfig().getBoolean("zamknij-po-odebraniu", false)) {
                    later(p::closeInventory);
                } else {
                    gui.refreshMenu(top, p);
                }
            }
            return;
        }

        Kit kit = gh.getKitId() == null ? null : manager.get(gh.getKitId());
        if (kit == null) {
            later(p::closeInventory);
            return;
        }

        if (type == GuiHolder.Type.PREVIEW) {
            if (raw == GuiManager.PREV_BACK) {
                later(() -> gui.openMenu(p));
            } else if (raw == GuiManager.PREV_CLAIM) {
                KitManager.Status st = manager.claim(p, kit);
                if (st == KitManager.Status.OK && plugin.getConfig().getBoolean("zamknij-po-odebraniu", false)) {
                    later(p::closeInventory);
                } else {
                    gui.refreshPreviewBar(top, p, kit);
                }
            }
            return;
        }

        // reszta to okna administracyjne
        if (!admin) {
            later(p::closeInventory);
            return;
        }

        switch (type) {
            case EDITOR:
                handleEditor(p, top, kit, raw, click, e);
                break;
            case SLOTS:
                handleSlots(p, top, kit, raw);
                break;
            case COOLDOWN:
                handleCooldown(p, top, kit, raw, click);
                break;
            default:
                break;
        }
    }

    private void handleEditor(Player p, Inventory top, Kit kit, int raw, ClickType click, InventoryClickEvent e) {
        if (raw == GuiManager.E_ICON) {
            ItemStack cursor = e.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                ItemStack icon = cursor.clone();
                icon.setAmount(1);
                kit.setIcon(icon);
                manager.saveKits();
                gui.refreshEditor(top, kit);
                plugin.sound(p, "entity.experience_orb.pickup", 1.2f);
                plugin.tell(p, "ikona-ustawiona", "&aUstawiono nową ikonę kitu.");
            } else {
                plugin.tell(p, "ikona-podpowiedz", "&7Weź przedmiot na kursor i kliknij nim w ikonę kitu.");
            }
        } else if (raw == GuiManager.E_NAME) {
            later(() -> gui.startPrompt(p, GuiManager.PromptKind.NAME, kit));
        } else if (raw == GuiManager.E_CONTENT) {
            later(() -> gui.openContents(p, kit));
        } else if (raw == GuiManager.E_SLOT) {
            later(() -> gui.openSlots(p, kit));
        } else if (raw == GuiManager.E_COOLDOWN) {
            later(() -> gui.openCooldown(p, kit));
        } else if (raw == GuiManager.E_RANK) {
            if (isLeft(click)) manager.cycleRank(kit, 1);
            else if (isRight(click)) manager.cycleRank(kit, -1);
            else return;
            manager.saveKits();
            gui.refreshEditor(top, kit);
            plugin.sound(p, "ui.button.click", 1.2f);
        } else if (raw == GuiManager.E_ENABLED) {
            kit.setEnabled(!kit.isEnabled());
            manager.saveKits();
            gui.refreshEditor(top, kit);
            plugin.sound(p, "ui.button.click", 1.2f);
        } else if (raw == GuiManager.E_COPY) {
            if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
                int n = manager.copyFromInventory(p, kit);
                gui.refreshEditor(top, kit);
                plugin.sound(p, "entity.experience_orb.pickup", 1.2f);
                plugin.tell(p, "skopiowano", "&aWczytano zawartość z ekwipunku: &f%ilosc% &aprzedmiotów.", "%ilosc%", String.valueOf(n));
            } else {
                plugin.tell(p, "kopiuj-podpowiedz", "&7Aby nadpisać zawartość kitu swoim ekwipunkiem, kliknij z &fShift&7.");
            }
        } else if (raw == GuiManager.E_PREVIEW) {
            later(() -> gui.openPreview(p, kit));
        } else if (raw == GuiManager.E_DELETE) {
            if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
                final String id = kit.getId();
                manager.delete(id);
                plugin.tell(p, "usunieto", "&cUsunięto kit &f%kit%&c.", "%kit%", id);
                plugin.sound(p, "entity.villager.no", 1f);
                later(() -> gui.openMenu(p));
            } else {
                plugin.tell(p, "usun-podpowiedz", "&7Aby usunąć kit, kliknij z wciśniętym &fShift&7.");
            }
        } else if (raw == GuiManager.E_BACK) {
            later(() -> gui.openMenu(p));
        }
    }

    private void handleSlots(Player p, Inventory top, Kit kit, int raw) {
        if (raw == GuiManager.SLOTS_BACK) {
            later(() -> gui.openEditor(p, kit));
            return;
        }
        if (raw < 0 || raw >= KitManager.MENU_SIZE || raw == KitManager.RESERVED_SLOT) return;
        Kit other = manager.kitAt(raw);
        if (other == kit) return;
        if (other != null) {
            other.setSlot(kit.getSlot());
        }
        kit.setSlot(raw);
        manager.saveKits();
        gui.refreshSlots(top, kit);
        plugin.sound(p, "ui.button.click", 1.2f);
    }

    private void handleCooldown(Player p, Inventory top, Kit kit, int raw, ClickType click) {
        if (raw == GuiManager.CD_BACK) {
            later(() -> gui.openEditor(p, kit));
            return;
        }
        if (raw == GuiManager.CD_NONE) {
            kit.setCooldown(0L);
        } else if (raw == GuiManager.CD_ONCE) {
            kit.setCooldown(-1L);
        } else if (raw == GuiManager.CD_CHAT) {
            later(() -> gui.startPrompt(p, GuiManager.PromptKind.COOLDOWN, kit));
            return;
        } else if (raw >= 10 && raw < 10 + GuiManager.CD_STEPS.length) {
            long step = GuiManager.CD_STEPS[raw - 10];
            long cur = Math.max(kit.getCooldown(), 0L);
            if (isLeft(click)) cur += step;
            else if (isRight(click)) cur -= step;
            else return;
            cur = Math.max(0L, Math.min(cur, KitManager.COOLDOWN_MAX));
            kit.setCooldown(cur);
        } else {
            return;
        }
        manager.saveKits();
        gui.refreshCooldown(top, kit);
        plugin.sound(p, "ui.button.click", 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof GuiHolder)) return;
        GuiHolder.Type type = ((GuiHolder) holder).getType();
        for (int raw : e.getRawSlots()) {
            if (raw >= top.getSize()) continue;
            if (type == GuiHolder.Type.CONTENTS) {
                if (raw >= KitManager.CONTENT_SIZE) {
                    e.setCancelled(true);
                    return;
                }
            } else {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        Inventory inv = e.getInventory();
        InventoryHolder holder = inv.getHolder();
        if (holder instanceof GuiHolder) {
            GuiHolder.Type t = ((GuiHolder) holder).getType();
            if (t == GuiHolder.Type.CONTENTS) {
                gui.saveContents(inv);
                gui.contentsClosed(e.getPlayer().getUniqueId(), inv);
            } else if (t == GuiHolder.Type.MENU) {
                gui.menuClosed(e.getPlayer().getUniqueId(), inv);
            }
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        final Player p = e.getPlayer();
        if (gui.getPrompt(p.getUniqueId()) == null) return;
        e.setCancelled(true);
        final String msg = e.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> gui.handlePrompt(p, msg));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        gui.clearPrompt(e.getPlayer().getUniqueId());
        gui.flushPlayer(e.getPlayer().getUniqueId());
    }
}
