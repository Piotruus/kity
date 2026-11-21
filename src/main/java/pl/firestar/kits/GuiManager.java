package pl.firestar.kits;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuiManager {

    public enum PromptKind { NAME, COOLDOWN }

    public static final class Prompt {
        public final PromptKind kind;
        public final String kitId;

        public Prompt(PromptKind kind, String kitId) {
            this.kind = kind;
            this.kitId = kitId;
        }
    }

    // sloty edytora kitu (45 slotow)
    public static final int E_ICON = 10;
    public static final int E_NAME = 11;
    public static final int E_CONTENT = 12;
    public static final int E_SLOT = 13;
    public static final int E_COOLDOWN = 14;
    public static final int E_RANK = 15;
    public static final int E_ENABLED = 16;
    public static final int E_PREVIEW = 30;
    public static final int E_DELETE = 32;
    public static final int E_BACK = 36;

    public static final int E_COPY = 28;

    public static final int MENU_HEAD = 4;        // glowa gracza z informacjami
    public static final int MENU_CLOSE = 49;      // przycisk zamknij

    public static final int PREV_BACK = 45;       // podglad: wroc
    public static final int PREV_INFO = 49;       // podglad: informacje o kicie
    public static final int PREV_CLAIM = 53;      // podglad: odbierz

    public static final int BACK_SLOT = 49;       // edytor zawartosci: wroc
    public static final int SLOTS_BACK = 53;      // wybor slotu

    // sloty edytora cooldownu (27 slotow)
    public static final int CD_BACK = 18;
    public static final int CD_NONE = 20;
    public static final int CD_CHAT = 22;
    public static final int CD_ONCE = 24;
    public static final long[] CD_STEPS = {10L, 60L, 600L, 3600L, 21600L, 86400L, 604800L};
    private static final String[] CD_NAMES = {"10 sekund", "1 minuta", "10 minut", "1 godzina", "6 godzin", "1 dzień", "7 dni"};

    private static final List<String> DEFAULT_LORE = Arrays.asList(
            "", "&7Ranga: %ranga%", "&7Cooldown: &f%cooldown%", "&7Przedmiotów: &f%ilosc%", "",
            "%status%", "", "&eLPM &7- odbierz kit", "&ePPM &7- podgląd zawartości");

    private final Main plugin;
    private final KitManager manager;
    private final Map<UUID, Inventory> openContents = new HashMap<>();
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();
    private final Map<UUID, Inventory> openMenus = new HashMap<>();

    public GuiManager(Main plugin, KitManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /* ------------------------------------------------------------ pomocnicze */

    private Material mat(String key, Material def) {
        String n = plugin.getConfig().getString(key);
        if (n == null) return def;
        Material m = Material.matchMaterial(n);
        return m == null ? def : m;
    }

    private void fillDecor(Inventory inv) {
        Material bg = mat("tlo-material", Material.GRAY_STAINED_GLASS_PANE);
        Material ring = mat("ramka-material", Material.PURPLE_STAINED_GLASS_PANE);
        int rows = inv.getSize() / 9;
        for (int i = 0; i < inv.getSize(); i++) {
            int r = i / 9;
            int c = i % 9;
            boolean border = r == 0 || r == rows - 1 || c == 0 || c == 8;
            inv.setItem(i, Items.make(border ? ring : bg, " "));
        }
    }

    private void fillBottomRow(Inventory inv) {
        Material ring = mat("ramka-material", Material.PURPLE_STAINED_GLASS_PANE);
        for (int i = 45; i < 54; i++) inv.setItem(i, Items.make(ring, " "));
    }

    private String statusText(Player p, Kit k) {
        if (!k.isEnabled()) {
            return plugin.getConfig().getString("status-wylaczony", "&8✘ Kit wyłączony (widzą go tylko admini)");
        }
        switch (manager.status(p, k)) {
            case NO_RANK:
                return plugin.getConfig().getString("status-ranga", "&c✘ Wymaga rangi %ranga%")
                        .replace("%ranga%", manager.rankName(k.getRank()));
            case COOLDOWN:
                return plugin.getConfig().getString("status-cooldown", "&c✘ Odbierzesz za &f%czas%")
                        .replace("%czas%", KitManager.formatDuration(manager.remaining(p, k)));
            case ONCE:
                return plugin.getConfig().getString("status-jednorazowy", "&c✘ Jednorazowy - już odebrany");
            case DISABLED:
                return plugin.getConfig().getString("status-wylaczony", "&8✘ Kit wyłączony (widzą go tylko admini)");
            default:
                return plugin.getConfig().getString("status-dostepny", "&a✔ Dostępny do odebrania");
        }
    }

    /* ------------------------------------------------------------ menu glowne */

    public void openMenu(Player p) {
        GuiHolder h = new GuiHolder(GuiHolder.Type.MENU, null);
        Inventory inv = Bukkit.createInventory(h, KitManager.MENU_SIZE, plugin.text("menu-tytul", "&8ᴡʏʙɪᴇʀᴢ ᴋɪᴛ"));
        h.setInventory(inv);
        refreshMenu(inv, p);
        p.openInventory(inv);
        openMenus.put(p.getUniqueId(), inv);
    }

    public void menuClosed(UUID uuid, Inventory inv) {
        openMenus.remove(uuid, inv);
    }

    /** Co sekunde odswieza otwarte menu graczy, ktorzy maja jakis kit na cooldownie. */
    public void tickMenus() {
        for (Map.Entry<UUID, Inventory> e : new ArrayList<>(openMenus.entrySet())) {
            Player pl = Bukkit.getPlayer(e.getKey());
            if (pl == null || !pl.isOnline()) {
                openMenus.remove(e.getKey());
                continue;
            }
            boolean countdown = false;
            for (Kit k : manager.all()) {
                if (k.isEnabled() && manager.status(pl, k) == KitManager.Status.COOLDOWN) {
                    countdown = true;
                    break;
                }
            }
            if (countdown) refreshMenu(e.getValue(), pl);
        }
    }

    public void refreshMenu(Inventory inv, Player p) {
        fillDecor(inv);
        boolean admin = p.hasPermission("firestarkits.admin");
        int available = 0;
        int total = 0;
        for (Kit k : manager.all()) {
            if (!k.isEnabled() && !admin) continue;
            total++;
            if (k.isEnabled() && manager.status(p, k) == KitManager.Status.OK) available++;
            if (k.getSlot() < 0 || k.getSlot() >= inv.getSize()) continue;
            inv.setItem(k.getSlot(), kitItem(p, k, admin));
        }
        if (manager.kitAt(MENU_HEAD) == null) {
            inv.setItem(MENU_HEAD, headItem(p, available, total));
        }
        if (manager.kitAt(MENU_CLOSE) == null) {
            inv.setItem(MENU_CLOSE, Items.make(Material.BARRIER, "&c&lZamknij"));
        }
    }

    private ItemStack headItem(Player p, int available, int total) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta m = head.getItemMeta();
        if (m instanceof SkullMeta) {
            try {
                ((SkullMeta) m).setOwningPlayer(p);
                head.setItemMeta(m);
            } catch (Exception ignored) {
                // glowa bez skina nie jest krytyczna
            }
        }
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&7Twoja ranga: " + manager.rankName(manager.highestRank(p)));
        lore.add("&7Dostępne kity: &a" + available + "&7/&f" + total);
        double mult = manager.cooldownMultiplier(p);
        if (mult < 1.0) {
            lore.add("&7Krótszy cooldown: &a-" + Math.round((1.0 - mult) * 100.0) + "%");
        }
        lore.add("");
        lore.add("&eLPM &7na kit - odbierz");
        lore.add("&ePPM &7na kit - podgląd zawartości");
        return Items.decorate(head, "&d&l" + p.getName(), lore);
    }

    /** Tekst cooldownu z uwzglednieniem rangi gracza, np. "12h (-25%)". */
    private String cooldownText(Player p, Kit k) {
        long base = k.getCooldown();
        if (base <= 0) return KitManager.formatDuration(base);
        long eff = manager.effectiveCooldown(p, k);
        String text = KitManager.formatDuration(eff);
        if (eff < base) {
            long pct = Math.round((1.0 - (double) eff / (double) base) * 100.0);
            text += " &8(&a-" + pct + "%&8)";
        }
        return text;
    }

    private ItemStack kitItem(Player p, Kit k, boolean admin) {
        ItemStack it = k.getIcon().clone();
        it.setAmount(1);
        List<String> tpl = plugin.getConfig().getStringList("menu-lore");
        if (tpl.isEmpty()) tpl = DEFAULT_LORE;
        String status = statusText(p, k);
        String cd = cooldownText(p, k);
        List<String> lore = new ArrayList<>();
        for (String line : tpl) {
            lore.add(line
                    .replace("%ranga%", manager.rankName(k.getRank()))
                    .replace("%cooldown%", cd)
                    .replace("%ilosc%", String.valueOf(k.countItems()))
                    .replace("%status%", status));
        }
        if (admin) {
            lore.add("");
            lore.add("&dShift + PPM &7- edytuj kit (admin)");
        }
        // dostepny kit swieci
        if (k.isEnabled() && manager.status(p, k) == KitManager.Status.OK) {
            Items.glow(it);
        }
        return Items.decorate(it, k.getName(), lore);
    }

    /* ------------------------------------------------------------ podglad */

    public void openPreview(Player p, Kit k) {
        GuiHolder h = new GuiHolder(GuiHolder.Type.PREVIEW, k.getId());
        Inventory inv = Bukkit.createInventory(h, 54, Items.color("&8Podgląd: &r" + k.getName()));
        h.setInventory(inv);
        for (int i = 0; i < KitManager.CONTENT_SIZE; i++) {
            ItemStack it = k.getItems()[i];
            if (it != null && it.getType() != Material.AIR) inv.setItem(i, it.clone());
        }
        refreshPreviewBar(inv, p, k);
        p.openInventory(inv);
    }

    /** Dolny pasek podgladu: wroc, informacje i przycisk odbioru. */
    public void refreshPreviewBar(Inventory inv, Player p, Kit k) {
        fillBottomRow(inv);
        inv.setItem(PREV_BACK, Items.make(Material.ARROW, "&7« Wróć do menu kitów"));

        String status = statusText(p, k);
        ItemStack icon = k.getIcon().clone();
        icon.setAmount(1);
        inv.setItem(PREV_INFO, Items.decorate(icon, k.getName(), Arrays.asList(
                "",
                "&7Ranga: " + manager.rankName(k.getRank()),
                "&7Cooldown: &f" + cooldownText(p, k),
                "&7Przedmiotów: &f" + k.countItems(),
                "",
                status)));

        boolean ok = k.isEnabled() && manager.status(p, k) == KitManager.Status.OK;
        if (ok) {
            inv.setItem(PREV_CLAIM, Items.make(Material.EMERALD, "&a&lODBIERZ KIT",
                    "", "&7Kliknij, aby odebrać ten kit."));
        } else {
            inv.setItem(PREV_CLAIM, Items.make(Material.RED_DYE, "&c&lNiedostępny",
                    "", status));
        }
    }

    /* ------------------------------------------------------------ edytor glowny */

    public void openEditor(Player p, Kit k) {
        GuiHolder h = new GuiHolder(GuiHolder.Type.EDITOR, k.getId());
        Inventory inv = Bukkit.createInventory(h, 45, Items.color("&8Edytor kitu: &d" + k.getId()));
        h.setInventory(inv);
        refreshEditor(inv, k);
        p.openInventory(inv);
    }

    public void refreshEditor(Inventory inv, Kit k) {
        fillDecor(inv);

        ItemStack icon = k.getIcon().clone();
        icon.setAmount(1);
        inv.setItem(E_ICON, Items.decorate(icon, "&d&lIkona kitu", Arrays.asList(
                "", "&7To ten przedmiot jest widoczny w menu.", "",
                "&eKliknij tutaj z przedmiotem na kursorze,", "&eaby ustawić nową ikonę.")));

        inv.setItem(E_NAME, Items.make(Material.NAME_TAG, "&e&lNazwa wyświetlana",
                "", "&7Aktualna: &r" + k.getName(), "",
                "&eKliknij &7aby wpisać nową na czacie", "&7(możesz używać kolorów &f&&7)"));

        inv.setItem(E_CONTENT, Items.make(Material.CHEST, "&6&lZawartość kitu",
                "", "&7Przedmiotów: &f" + k.countItems(), "",
                "&eKliknij &7aby edytować zawartość"));

        int s = k.getSlot();
        inv.setItem(E_SLOT, Items.make(Material.COMPASS, "&b&lSlot w menu",
                "", "&7Aktualny: &frząd " + (s / 9 + 1) + ", kolumna " + (s % 9 + 1) + " &8(slot " + s + ")", "",
                "&eKliknij &7aby wybrać miejsce w menu"));

        inv.setItem(E_COOLDOWN, Items.make(Material.CLOCK, "&a&lCooldown",
                "", "&7Aktualny: &f" + KitManager.formatDuration(k.getCooldown()), "",
                "&eKliknij &7aby zmienić"));

        List<String> rankLore = new ArrayList<>();
        rankLore.add("");
        for (String id : manager.rankIds()) {
            rankLore.add((id.equals(k.getRank()) ? "&a▶ " : "&8   ") + manager.rankName(id));
        }
        rankLore.add("");
        rankLore.add("&eLPM &7- następna ranga");
        rankLore.add("&ePPM &7- poprzednia ranga");
        inv.setItem(E_RANK, Items.make(Material.DIAMOND, "&3&lWymagana ranga", rankLore));

        if (k.isEnabled()) {
            inv.setItem(E_ENABLED, Items.make(Material.LIME_DYE, "&a&lKit aktywny",
                    "", "&7Gracze mogą go odbierać.", "", "&eKliknij &7aby wyłączyć"));
        } else {
            inv.setItem(E_ENABLED, Items.make(Material.GRAY_DYE, "&c&lKit wyłączony",
                    "", "&7Widzą go tylko admini.", "", "&eKliknij &7aby włączyć"));
        }

        inv.setItem(E_COPY, Items.make(Material.HOPPER, "&6&lWczytaj z ekwipunku",
                "", "&7Ustawia zawartość kitu na Twój", "&7aktualny ekwipunek i zbroję.", "",
                "&cShift + klik &7aby nadpisać"));

        inv.setItem(E_PREVIEW, Items.make(Material.ENDER_EYE, "&f&lPodgląd", "", "&eKliknij &7aby zobaczyć kit oczami gracza"));
        inv.setItem(E_DELETE, Items.make(Material.BARRIER, "&c&lUsuń kit", "", "&7Nieodwracalne!", "&cShift + klik &7aby usunąć"));
        inv.setItem(E_BACK, Items.make(Material.ARROW, "&7« Wróć do menu kitów"));
    }

    /* ------------------------------------------------------------ wybor slotu */

    public void openSlots(Player p, Kit k) {
        GuiHolder h = new GuiHolder(GuiHolder.Type.SLOTS, k.getId());
        Inventory inv = Bukkit.createInventory(h, 54, Items.color("&8Wybierz slot: &d" + k.getId()));
        h.setInventory(inv);
        refreshSlots(inv, k);
        p.openInventory(inv);
        plugin.tell(p, "slot-info", "&7Kliknij miejsce w menu, w którym ma być kit. Kliknięcie innego kitu zamieni je miejscami.");
    }

    public void refreshSlots(Inventory inv, Kit k) {
        fillDecor(inv);
        for (Kit other : manager.all()) {
            if (other.getSlot() < 0 || other.getSlot() >= inv.getSize()) continue;
            ItemStack it = other.getIcon().clone();
            it.setAmount(1);
            if (other == k) {
                inv.setItem(other.getSlot(), Items.decorate(it, "&a&l▶ " + other.getName(),
                        Arrays.asList("", "&aTo jest aktualne miejsce tego kitu.")));
            } else {
                inv.setItem(other.getSlot(), Items.decorate(it, other.getName(),
                        Arrays.asList("", "&7Zajęty przez inny kit.", "&eKliknij &7aby zamienić miejscami")));
            }
        }
        inv.setItem(SLOTS_BACK, Items.make(Material.ARROW, "&7« Wróć do edytora"));
    }

    /* ------------------------------------------------------------ cooldown */

    public void openCooldown(Player p, Kit k) {
        GuiHolder h = new GuiHolder(GuiHolder.Type.COOLDOWN, k.getId());
        Inventory inv = Bukkit.createInventory(h, 27, Items.color("&8Cooldown: &d" + k.getId()));
        h.setInventory(inv);
        refreshCooldown(inv, k);
        p.openInventory(inv);
    }

    public void refreshCooldown(Inventory inv, Kit k) {
        fillDecor(inv);
        inv.setItem(4, Items.make(Material.CLOCK, "&6&lCooldown kitu",
                "", "&7Aktualny: &f" + KitManager.formatDuration(k.getCooldown())));
        for (int i = 0; i < CD_STEPS.length; i++) {
            inv.setItem(10 + i, Items.make(Material.PAPER, "&e" + CD_NAMES[i],
                    "", "&aLPM &7- dodaj", "&cPPM &7- odejmij"));
        }
        inv.setItem(CD_NONE, Items.make(Material.FEATHER, "&a&lBez cooldownu", "", "&7Kit można odbierać bez ograniczeń."));
        inv.setItem(CD_CHAT, Items.make(Material.WRITABLE_BOOK, "&e&lWpisz na czacie",
                "", "&7Np. &f1d12h30m &7albo &f45m", "", "&eKliknij &7aby wpisać"));
        inv.setItem(CD_ONCE, Items.make(Material.ENDER_PEARL, "&5&lJednorazowy", "", "&7Każdy gracz odbierze go tylko raz."));
        inv.setItem(CD_BACK, Items.make(Material.ARROW, "&7« Wróć do edytora"));
    }

    /* ------------------------------------------------------------ zawartosc */

    public void openContents(Player p, Kit k) {
        GuiHolder h = new GuiHolder(GuiHolder.Type.CONTENTS, k.getId());
        Inventory inv = Bukkit.createInventory(h, 54, Items.color("&8Zawartość: &d" + k.getId()));
        h.setInventory(inv);
        fillBottomRow(inv);
        for (int i = 0; i < KitManager.CONTENT_SIZE; i++) {
            ItemStack it = k.getItems()[i];
            if (it != null && it.getType() != Material.AIR) inv.setItem(i, it.clone());
        }
        inv.setItem(45, Items.make(Material.BOOK, "&e&lZawartość kitu",
                "", "&7Wkładaj i wyjmuj przedmioty.", "&7Zapisuje się automatycznie", "&7po zamknięciu okna."));
        inv.setItem(BACK_SLOT, Items.make(Material.ARROW, "&7« Wróć do edytora"));
        openContents.put(p.getUniqueId(), inv);
        p.openInventory(inv);
    }

    public void saveContents(Inventory inv) {
        if (!(inv.getHolder() instanceof GuiHolder)) return;
        GuiHolder h = (GuiHolder) inv.getHolder();
        if (h.getType() != GuiHolder.Type.CONTENTS) return;
        Kit k = manager.get(h.getKitId());
        if (k == null) return;
        ItemStack[] arr = k.getItems();
        for (int i = 0; i < KitManager.CONTENT_SIZE; i++) {
            ItemStack it = inv.getItem(i);
            arr[i] = (it == null || it.getType() == Material.AIR) ? null : it.clone();
        }
        manager.saveKits();
    }

    public void contentsClosed(UUID uuid, Inventory inv) {
        openContents.remove(uuid, inv);
    }

    public void flushPlayer(UUID uuid) {
        Inventory inv = openContents.remove(uuid);
        if (inv != null) saveContents(inv);
    }

    public void flushOpenEditors() {
        for (Inventory inv : new ArrayList<>(openContents.values())) saveContents(inv);
        openContents.clear();
    }

    /* ------------------------------------------------------------ chat prompt */

    public Prompt getPrompt(UUID uuid) {
        return prompts.get(uuid);
    }

    public void clearPrompt(UUID uuid) {
        prompts.remove(uuid);
    }

    public void startPrompt(Player p, PromptKind kind, Kit k) {
        prompts.put(p.getUniqueId(), new Prompt(kind, k.getId()));
        p.closeInventory();
        if (kind == PromptKind.NAME) {
            plugin.tell(p, "prompt-nazwa", "&eWpisz na czacie nową nazwę kitu (możesz używać kolorów &f&&e). Wpisz &fanuluj &eaby przerwać.");
        } else {
            plugin.tell(p, "prompt-cooldown", "&eWpisz na czacie cooldown, np. &f1d12h30m&e, &f45m&e, &f0 &e(brak) lub &fjednorazowy&e. Wpisz &fanuluj &eaby przerwać.");
        }
    }

    /** Wywolywane na glownym watku z tekstem wpisanym przez gracza. */
    public void handlePrompt(Player p, String raw) {
        Prompt pr = prompts.get(p.getUniqueId());
        if (pr == null) return;
        if (!p.hasPermission("firestarkits.admin")) {
            prompts.remove(p.getUniqueId());
            return;
        }
        Kit k = manager.get(pr.kitId);
        if (k == null) {
            prompts.remove(p.getUniqueId());
            return;
        }
        String msg = raw.trim();
        if (msg.equalsIgnoreCase("anuluj")) {
            prompts.remove(p.getUniqueId());
            plugin.tell(p, "anulowano", "&7Anulowano.");
            openEditor(p, k);
            return;
        }
        if (pr.kind == PromptKind.NAME) {
            if (msg.isEmpty() || msg.length() > 64) {
                plugin.tell(p, "zla-nazwa", "&cNazwa musi mieć od 1 do 64 znaków. Spróbuj jeszcze raz albo wpisz &fanuluj&c.");
                return;
            }
            k.setName(msg);
        } else {
            Long v = KitManager.parseDuration(msg);
            if (v == null) {
                plugin.tell(p, "zly-czas", "&cNieprawidłowy format. Przykłady: &f1d12h30m&c, &f45m&c, &f0&c, &fjednorazowy&c. Spróbuj jeszcze raz albo wpisz &fanuluj&c.");
                return;
            }
            k.setCooldown(v);
        }
        prompts.remove(p.getUniqueId());
        manager.saveKits();
        plugin.sound(p, "entity.experience_orb.pickup", 1.2f);
        openEditor(p, k);
    }
}
