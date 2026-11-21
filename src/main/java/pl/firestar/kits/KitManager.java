package pl.firestar.kits;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KitManager {

    public static final int CONTENT_SIZE = 45;
    public static final int MENU_SIZE = 54;
    public static final int RESERVED_SLOT = 53; // w edytorze slotow jest tam przycisk "wroc"
    public static final int INFO_SLOT = 49;
    public static final long COOLDOWN_MAX = 31536000L; // 365 dni

    public static final Set<String> RESERVED_IDS = new HashSet<>(Arrays.asList(
            "create", "delete", "edit", "give", "reset", "setcooldown", "setname",
            "setrank", "setitems", "list", "reload", "help"));

    public enum Status { OK, DISABLED, NO_RANK, COOLDOWN, ONCE }

    private final Main plugin;
    private final File dataDir;
    private final File kitsFile;
    private final File claimsFile;
    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Long>> claims = new HashMap<>();
    private boolean claimsDirty = false;

    public KitManager(Main plugin) {
        this.plugin = plugin;
        this.dataDir = plugin.getDataFolder();
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            plugin.getLogger().severe("Nie mozna utworzyc folderu pluginu!");
        }
        this.kitsFile = new File(dataDir, "kits.yml");
        this.claimsFile = new File(dataDir, "cooldowns.yml");
    }

    /* ------------------------------------------------------------ pliki */

    private YamlConfiguration readYaml(File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        if (!file.exists()) return yaml;
        try {
            yaml.load(file);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Uszkodzony plik " + file.getName() + " - robie kopie i zaczynam od nowa", ex);
            File bak = new File(dataDir, file.getName() + ".corrupt-" + System.currentTimeMillis());
            if (!file.renameTo(bak)) {
                plugin.getLogger().severe("Nie udalo sie zrobic kopii uszkodzonego pliku " + file.getName());
            }
            yaml = new YamlConfiguration();
        }
        return yaml;
    }

    private void writeYaml(YamlConfiguration yaml, File target) {
        File tmp = new File(dataDir, target.getName() + ".tmp");
        try {
            yaml.save(tmp);
            try {
                Files.move(tmp.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Nie udalo sie zapisac pliku " + target.getName(), ex);
        }
    }

    public void load() {
        kits.clear();
        claims.clear();

        YamlConfiguration y = readYaml(kitsFile);
        ConfigurationSection root = y.getConfigurationSection("kits");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) continue;
                Kit k = new Kit(id.toLowerCase(Locale.ROOT));
                k.setName(s.getString("name", id));
                ItemStack icon = s.getItemStack("icon");
                k.setIcon(icon == null || icon.getType() == Material.AIR ? new ItemStack(Material.CHEST) : icon);
                k.setSlot(s.getInt("slot", -1));
                k.setCooldown(s.getLong("cooldown", 0L));
                k.setRank(s.getString("rank", "gracz"));
                k.setEnabled(s.getBoolean("enabled", true));
                ConfigurationSection is = s.getConfigurationSection("items");
                if (is != null) {
                    for (String key : is.getKeys(false)) {
                        if (!key.startsWith("s")) continue;
                        try {
                            int i = Integer.parseInt(key.substring(1));
                            if (i < 0 || i >= CONTENT_SIZE) continue;
                            ItemStack it = is.getItemStack(key);
                            if (it != null && it.getType() != Material.AIR) k.getItems()[i] = it;
                        } catch (NumberFormatException ignored) {
                            // pomijamy
                        }
                    }
                }
                kits.put(k.getId(), k);
            }
        }

        // naprawa slotow (brak, zly, zduplikowany)
        Set<Integer> used = new HashSet<>();
        for (Kit k : kits.values()) {
            int s = k.getSlot();
            if (s < 0 || s >= MENU_SIZE || s == RESERVED_SLOT || !used.add(s)) {
                s = nextFreeSlot(used);
                k.setSlot(s);
                used.add(s);
            }
        }

        YamlConfiguration c = readYaml(claimsFile);
        ConfigurationSection cs = c.getConfigurationSection("claims");
        if (cs != null) {
            for (String uuidStr : cs.getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidStr);
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                ConfigurationSection sec = cs.getConfigurationSection(uuidStr);
                if (sec == null) continue;
                Map<String, Long> map = new HashMap<>();
                for (String kitId : sec.getKeys(false)) {
                    map.put(kitId, sec.getLong(kitId));
                }
                claims.put(uuid, map);
            }
        }
        claimsDirty = false;
    }

    public void saveKits() {
        YamlConfiguration y = new YamlConfiguration();
        for (Kit k : kits.values()) {
            String b = "kits." + k.getId();
            y.set(b + ".name", k.getName());
            y.set(b + ".icon", k.getIcon());
            y.set(b + ".slot", k.getSlot());
            y.set(b + ".cooldown", k.getCooldown());
            y.set(b + ".rank", k.getRank());
            y.set(b + ".enabled", k.isEnabled());
            ItemStack[] items = k.getItems();
            for (int i = 0; i < items.length; i++) {
                if (items[i] != null && items[i].getType() != Material.AIR) {
                    y.set(b + ".items.s" + i, items[i]);
                }
            }
        }
        writeYaml(y, kitsFile);
    }

    public void saveClaims() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Long>> e : claims.entrySet()) {
            for (Map.Entry<String, Long> k : e.getValue().entrySet()) {
                y.set("claims." + e.getKey() + "." + k.getKey(), k.getValue());
            }
        }
        writeYaml(y, claimsFile);
        claimsDirty = false;
    }

    public void saveClaimsIfDirty() {
        if (claimsDirty) saveClaims();
    }

    public void saveAll() {
        saveKits();
        saveClaims();
    }

    /* ------------------------------------------------------------ kity */

    public Collection<Kit> all() {
        return Collections.unmodifiableCollection(kits.values());
    }

    public Kit get(String id) {
        if (id == null) return null;
        return kits.get(id.toLowerCase(Locale.ROOT));
    }

    public Kit kitAt(int slot) {
        for (Kit k : kits.values()) {
            if (k.getSlot() == slot) return k;
        }
        return null;
    }

    public Kit create(String id, ItemStack icon) {
        Kit k = new Kit(id);
        if (icon != null && icon.getType() != Material.AIR) k.setIcon(icon);
        k.setSlot(nextFreeSlot());
        kits.put(id, k);
        saveKits();
        return k;
    }

    public void delete(String id) {
        kits.remove(id);
        for (Map<String, Long> m : claims.values()) m.remove(id);
        claimsDirty = true;
        saveKits();
        saveClaims();
    }

    private int nextFreeSlot() {
        Set<Integer> used = new HashSet<>();
        for (Kit k : kits.values()) used.add(k.getSlot());
        return nextFreeSlot(used);
    }

    private int nextFreeSlot(Set<Integer> used) {
        for (int r = 1; r <= 4; r++) {
            for (int c = 1; c <= 7; c++) {
                int s = r * 9 + c;
                if (!used.contains(s)) return s;
            }
        }
        for (int s = 0; s < MENU_SIZE; s++) {
            if (s != RESERVED_SLOT && !used.contains(s)) return s;
        }
        return 10;
    }

    /* ------------------------------------------------------------ rangi */

    public List<String> rankIds() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("rangi");
        if (s == null || s.getKeys(false).isEmpty()) {
            return new ArrayList<>(Collections.singletonList("gracz"));
        }
        return new ArrayList<>(s.getKeys(false));
    }

    public String rankName(String id) {
        return plugin.color(plugin.getConfig().getString("rangi." + id + ".nazwa", id));
    }

    public String rankPerm(String id) {
        String p = plugin.getConfig().getString("rangi." + id + ".permisja", "");
        return p == null ? "" : p;
    }

    public void cycleRank(Kit k, int dir) {
        List<String> ids = rankIds();
        int idx = ids.indexOf(k.getRank());
        if (idx < 0) idx = 0;
        idx = (idx + dir + ids.size()) % ids.size();
        k.setRank(ids.get(idx));
    }

    public boolean hasRank(Player p, Kit k) {
        List<String> ids = rankIds();
        int idx = ids.indexOf(k.getRank());
        if (idx < 0) return true;
        String perm = rankPerm(k.getRank());
        if (perm.isEmpty()) return true;
        if (p.hasPermission(perm)) return true;
        if (plugin.getConfig().getBoolean("hierarchia-rang", true)) {
            for (int j = idx + 1; j < ids.size(); j++) {
                String higher = rankPerm(ids.get(j));
                if (!higher.isEmpty() && p.hasPermission(higher)) return true;
            }
        }
        return false;
    }

    /* ------------------------------------------------------------ odbieranie */

    public Status status(Player p, Kit k) {
        if (p.hasPermission("firestarkits.bypass")) return Status.OK;
        if (!k.isEnabled()) return Status.DISABLED;
        if (!hasRank(p, k)) return Status.NO_RANK;
        Map<String, Long> map = claims.get(p.getUniqueId());
        Long last = map == null ? null : map.get(k.getId());
        if (last != null) {
            if (k.getCooldown() < 0) return Status.ONCE;
            if (k.getCooldown() > 0 && remaining(p, k) > 0) return Status.COOLDOWN;
        }
        return Status.OK;
    }

    /** Najwyzsza ranga, ktora ma gracz (id z config.yml). */
    public String highestRank(Player p) {
        List<String> ids = rankIds();
        for (int j = ids.size() - 1; j >= 0; j--) {
            String perm = rankPerm(ids.get(j));
            if (perm.isEmpty() || p.hasPermission(perm)) return ids.get(j);
        }
        return ids.get(0);
    }

    /** Mnoznik cooldownu zalezny od rangi (1.0 = bez zmian). */
    public double cooldownMultiplier(Player p) {
        double m = plugin.getConfig().getDouble("mnoznik-cooldownu." + highestRank(p), 1.0);
        if (m < 0.01) m = 0.01;
        return m;
    }

    /** Cooldown kitu w sekundach dla konkretnego gracza (z uwzglednieniem rangi). */
    public long effectiveCooldown(Player p, Kit k) {
        long base = k.getCooldown();
        if (base <= 0) return base;
        return Math.max(1L, Math.round(base * cooldownMultiplier(p)));
    }

    /** Pozostale sekundy cooldownu (0 jesli mozna odebrac). */
    public long remaining(Player p, Kit k) {
        Map<String, Long> map = claims.get(p.getUniqueId());
        Long last = map == null ? null : map.get(k.getId());
        long cd = effectiveCooldown(p, k);
        if (last == null || cd <= 0) return 0L;
        long leftMs = last + cd * 1000L - System.currentTimeMillis();
        if (leftMs <= 0) return 0L;
        return (leftMs + 999L) / 1000L;
    }

    /** Ustawia zawartosc kitu na ekwipunek i zbroje gracza. Zwraca liczbe przedmiotow. */
    public int copyFromInventory(Player p, Kit k) {
        ItemStack[] arr = k.getItems();
        Arrays.fill(arr, null);
        ItemStack[] storage = p.getInventory().getStorageContents();
        for (int i = 0; i < storage.length && i < 36; i++) {
            ItemStack it = storage[i];
            if (it != null && it.getType() != Material.AIR) arr[i] = it.clone();
        }
        ItemStack[] armor = p.getInventory().getArmorContents(); // buty, getry, napiersnik, helm
        for (int i = 0; i < armor.length && i < 4; i++) {
            ItemStack it = armor[i];
            if (it != null && it.getType() != Material.AIR) arr[36 + i] = it.clone();
        }
        saveKits();
        return k.countItems();
    }

    public Status claim(Player p, Kit k) {
        Status st = status(p, k);
        switch (st) {
            case DISABLED:
                plugin.tell(p, "wylaczony", "&cTen kit jest obecnie wyłączony.");
                plugin.sound(p, "entity.villager.no", 1f);
                return st;
            case NO_RANK:
                plugin.tell(p, "brak-rangi", "&cTen kit wymaga rangi %ranga%&c.", "%ranga%", rankName(k.getRank()));
                plugin.sound(p, "entity.villager.no", 1f);
                return st;
            case COOLDOWN:
                plugin.tell(p, "cooldown", "&cTen kit odbierzesz ponownie za &f%czas%&c.",
                        "%czas%", formatDuration(remaining(p, k)));
                plugin.sound(p, "entity.villager.no", 1f);
                return st;
            case ONCE:
                plugin.tell(p, "jednorazowy", "&cTen kit jest jednorazowy i został już przez Ciebie odebrany.");
                plugin.sound(p, "entity.villager.no", 1f);
                return st;
            default:
                break;
        }

        boolean dropped = give(p, k);
        if (k.getCooldown() != 0 && !p.hasPermission("firestarkits.bypass")) {
            claims.computeIfAbsent(p.getUniqueId(), x -> new HashMap<>()).put(k.getId(), System.currentTimeMillis());
            claimsDirty = true;
        }
        plugin.tell(p, "odebrano", "&aOdebrałeś kit &r%kit%&a!", "%kit%", k.getName());
        if (dropped) plugin.tell(p, "odebrano-drop", "&eCzęść przedmiotów nie zmieściła się w ekwipunku i wypadła pod Twoje nogi.");
        plugin.sound(p, "entity.player.levelup", 1.2f);
        if (plugin.getConfig().getBoolean("actionbar", true)) {
            plugin.actionBar(p, "&a✔ Odebrano kit &r" + k.getName());
        }
        return Status.OK;
    }

    /** Daje zawartosc kitu graczowi. Zwraca true, jesli cos wypadlo na ziemie. */
    public boolean give(Player p, Kit k) {
        boolean dropped = false;
        boolean auto = plugin.getConfig().getBoolean("auto-zaloz-zbroje", true);
        for (ItemStack src : k.getItems()) {
            if (src == null || src.getType() == Material.AIR) continue;
            ItemStack it = src.clone();
            if (auto && tryEquip(p, it)) continue;
            Map<Integer, ItemStack> left = p.getInventory().addItem(it);
            for (ItemStack rest : left.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), rest);
                dropped = true;
            }
        }
        return dropped;
    }

    private boolean tryEquip(Player p, ItemStack it) {
        if (it.getAmount() != 1) return false;
        String n = it.getType().name();
        PlayerInventory inv = p.getInventory();
        if (n.endsWith("_HELMET") && isAir(inv.getHelmet())) {
            inv.setHelmet(it);
            return true;
        }
        if ((n.endsWith("_CHESTPLATE") || n.equals("ELYTRA")) && isAir(inv.getChestplate())) {
            inv.setChestplate(it);
            return true;
        }
        if (n.endsWith("_LEGGINGS") && isAir(inv.getLeggings())) {
            inv.setLeggings(it);
            return true;
        }
        if (n.endsWith("_BOOTS") && isAir(inv.getBoots())) {
            inv.setBoots(it);
            return true;
        }
        return false;
    }

    private boolean isAir(ItemStack s) {
        return s == null || s.getType() == Material.AIR;
    }

    public void resetClaims(UUID uuid, String kitId) {
        Map<String, Long> map = claims.get(uuid);
        if (map == null) return;
        if (kitId == null) map.clear();
        else map.remove(kitId);
        claimsDirty = true;
        saveClaims();
    }

    /* ------------------------------------------------------------ czas */

    public static String formatDuration(long s) {
        if (s < 0) return "jednorazowy";
        if (s == 0) return "brak";
        long d = s / 86400;
        long h = (s % 86400) / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (sec > 0) sb.append(sec).append("s ");
        return sb.toString().trim();
    }

    /** Zwraca sekundy, 0 = brak, -1 = jednorazowy, null = zly format. */
    public static Long parseDuration(String input) {
        if (input == null) return null;
        String t = input.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return null;
        if (t.equals("0") || t.equals("brak") || t.equals("off") || t.equals("none")) return 0L;
        if (t.equals("-1") || t.equals("once") || t.equals("jednorazowy") || t.equals("raz")) return -1L;
        try {
            long total = 0L;
            if (t.matches("\\d{1,9}")) {
                total = Long.parseLong(t);
            } else if (t.matches("(\\d{1,9}[dhms])+")) {
                Matcher m = Pattern.compile("(\\d{1,9})([dhms])").matcher(t);
                while (m.find()) {
                    long v = Long.parseLong(m.group(1));
                    switch (m.group(2)) {
                        case "d": total += v * 86400L; break;
                        case "h": total += v * 3600L; break;
                        case "m": total += v * 60L; break;
                        default: total += v; break;
                    }
                }
            } else {
                return null;
            }
            return Math.min(total, COOLDOWN_MAX);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
