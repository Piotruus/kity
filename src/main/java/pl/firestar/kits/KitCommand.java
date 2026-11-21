package pl.firestar.kits;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class KitCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "firestarkits.admin";
    private static final List<String> SUBS = Arrays.asList(
            "create", "delete", "edit", "give", "reset", "setcooldown", "setname",
            "setrank", "setitems", "list", "reload", "help");

    private final Main plugin;
    private final KitManager manager;
    private final GuiManager gui;

    public KitCommand(Main plugin, KitManager manager, GuiManager gui) {
        this.plugin = plugin;
        this.manager = manager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                plugin.tell(sender, "tylko-gracz", "&cTa komenda jest tylko dla graczy.");
                return true;
            }
            gui.openMenu((Player) sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // /kit <nazwa> - odbierz bezposrednio
        if (!KitManager.RESERVED_IDS.contains(sub)) {
            if (!(sender instanceof Player)) {
                plugin.tell(sender, "tylko-gracz", "&cTa komenda jest tylko dla graczy.");
                return true;
            }
            Kit k = manager.get(sub);
            if (k == null) {
                plugin.tell(sender, "nie-ma", "&cNie ma takiego kitu.");
                return true;
            }
            manager.claim((Player) sender, k);
            return true;
        }

        if (!sender.hasPermission(ADMIN)) {
            plugin.tell(sender, "brak-uprawnien", "&cNie masz uprawnień do tej komendy.");
            return true;
        }

        switch (sub) {
            case "help":
                help(sender);
                return true;
            case "list":
                list(sender);
                return true;
            case "reload":
                plugin.reloadAll();
                plugin.tell(sender, "przeladowano", "&aPrzeładowano konfigurację i kity.");
                return true;
            case "create":
                create(sender, args);
                return true;
            case "delete":
                delete(sender, args);
                return true;
            case "edit":
                edit(sender, args);
                return true;
            case "give":
                give(sender, args);
                return true;
            case "reset":
                reset(sender, args);
                return true;
            case "setcooldown":
                setCooldown(sender, args);
                return true;
            case "setname":
                setName(sender, args);
                return true;
            case "setrank":
                setRank(sender, args);
                return true;
            case "setitems":
                setItems(sender, args);
                return true;
            default:
                help(sender);
                return true;
        }
    }

    private void usage(CommandSender s, String u) {
        plugin.tell(s, "uzycie", "&cUżycie: &f%u%", "%u%", u);
    }

    private Kit needKit(CommandSender s, String id) {
        Kit k = manager.get(id);
        if (k == null) plugin.tell(s, "nie-ma", "&cNie ma takiego kitu.");
        return k;
    }

    private void help(CommandSender s) {
        s.sendMessage(plugin.color("&d&l━━━━━ FirestarKits ━━━━━"));
        s.sendMessage(plugin.color("&f/kit &8- &7menu kitów"));
        s.sendMessage(plugin.color("&f/kit <nazwa> &8- &7odbierz kit"));
        s.sendMessage(plugin.color("&f/kit create <nazwa> &8- &7nowy kit (ikona = item w ręce)"));
        s.sendMessage(plugin.color("&f/kit edit <nazwa> &8- &7edytor w GUI"));
        s.sendMessage(plugin.color("&f/kit delete <nazwa> &8- &7usuń kit"));
        s.sendMessage(plugin.color("&f/kit setitems <nazwa> &8- &7zawartość = Twój ekwipunek"));
        s.sendMessage(plugin.color("&f/kit setcooldown <nazwa> <czas> &8- &7np. 1d12h, 30m, 0, jednorazowy"));
        s.sendMessage(plugin.color("&f/kit setname <nazwa> <tekst> &8- &7nazwa w menu"));
        s.sendMessage(plugin.color("&f/kit setrank <nazwa> <ranga> &8- &7wymagana ranga"));
        s.sendMessage(plugin.color("&f/kit give <nazwa> <gracz> &8- &7daj kit (bez cooldownu)"));
        s.sendMessage(plugin.color("&f/kit reset <gracz> [nazwa] &8- &7zresetuj cooldowny"));
        s.sendMessage(plugin.color("&f/kit list &8- &7lista kitów"));
        s.sendMessage(plugin.color("&f/kit reload &8- &7przeładuj"));
    }

    private void list(CommandSender s) {
        if (manager.all().isEmpty()) {
            plugin.tell(s, "brak-kitow", "&7Nie ma jeszcze żadnych kitów. Utwórz pierwszy: &f/kit create <nazwa>");
            return;
        }
        s.sendMessage(plugin.color("&d&lKity &8(" + manager.all().size() + ")"));
        for (Kit k : manager.all()) {
            s.sendMessage(plugin.color("&8 - &f" + k.getId() + " &8| &r" + k.getName()
                    + " &8| " + manager.rankName(k.getRank())
                    + " &8| &7" + KitManager.formatDuration(k.getCooldown())
                    + (k.isEnabled() ? "" : " &8| &cwyłączony")));
        }
    }

    private void create(CommandSender s, String[] args) {
        if (!(s instanceof Player)) {
            plugin.tell(s, "tylko-gracz", "&cTa komenda jest tylko dla graczy.");
            return;
        }
        if (args.length < 2) {
            usage(s, "/kit create <nazwa>");
            return;
        }
        Player p = (Player) s;
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_-]{1,24}")) {
            plugin.tell(s, "zle-id", "&cNazwa kitu: 1-24 znaków, tylko małe litery, cyfry, - i _.");
            return;
        }
        if (KitManager.RESERVED_IDS.contains(id)) {
            plugin.tell(s, "zarezerwowane", "&cTa nazwa jest zarezerwowana dla komendy.");
            return;
        }
        if (manager.get(id) != null) {
            plugin.tell(s, "istnieje", "&cKit o tej nazwie już istnieje.");
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        ItemStack icon = (hand == null || hand.getType() == Material.AIR) ? new ItemStack(Material.CHEST) : hand.clone();
        icon.setAmount(1);
        Kit k = manager.create(id, icon);
        plugin.tell(s, "utworzono", "&aUtworzono kit &f%kit%&a. Skonfiguruj go w edytorze.", "%kit%", id);
        gui.openEditor(p, k);
    }

    private void delete(CommandSender s, String[] args) {
        if (args.length < 2) {
            usage(s, "/kit delete <nazwa>");
            return;
        }
        Kit k = needKit(s, args[1]);
        if (k == null) return;
        manager.delete(k.getId());
        plugin.tell(s, "usunieto", "&cUsunięto kit &f%kit%&c.", "%kit%", k.getId());
    }

    private void edit(CommandSender s, String[] args) {
        if (!(s instanceof Player)) {
            plugin.tell(s, "tylko-gracz", "&cTa komenda jest tylko dla graczy.");
            return;
        }
        if (args.length < 2) {
            usage(s, "/kit edit <nazwa>");
            return;
        }
        Kit k = needKit(s, args[1]);
        if (k == null) return;
        gui.openEditor((Player) s, k);
    }

    private void give(CommandSender s, String[] args) {
        if (args.length < 3) {
            usage(s, "/kit give <nazwa> <gracz>");
            return;
        }
        Kit k = needKit(s, args[1]);
        if (k == null) return;
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            plugin.tell(s, "nie-ma-gracza", "&cGracz nie jest online.");
            return;
        }
        boolean dropped = manager.give(target, k);
        plugin.tell(target, "odebrano", "&aOdebrałeś kit &r%kit%&a!", "%kit%", k.getName());
        if (dropped) plugin.tell(target, "odebrano-drop", "&eCzęść przedmiotów nie zmieściła się w ekwipunku i wypadła pod Twoje nogi.");
        plugin.tell(s, "dano", "&aDano kit &f%kit% &agraczowi &f%gracz%&a.", "%kit%", k.getId(), "%gracz%", target.getName());
    }

    @SuppressWarnings("deprecation")
    private void reset(CommandSender s, String[] args) {
        if (args.length < 2) {
            usage(s, "/kit reset <gracz> [nazwa]");
            return;
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
        String kitId = null;
        if (args.length >= 3) {
            Kit k = needKit(s, args[2]);
            if (k == null) return;
            kitId = k.getId();
        }
        manager.resetClaims(op.getUniqueId(), kitId);
        plugin.tell(s, "reset", "&aZresetowano cooldowny gracza &f%gracz%&a.", "%gracz%", args[1]);
    }

    private void setCooldown(CommandSender s, String[] args) {
        if (args.length < 3) {
            usage(s, "/kit setcooldown <nazwa> <czas>");
            return;
        }
        Kit k = needKit(s, args[1]);
        if (k == null) return;
        Long v = KitManager.parseDuration(args[2]);
        if (v == null) {
            plugin.tell(s, "zly-czas", "&cNieprawidłowy format. Przykłady: &f1d12h30m&c, &f45m&c, &f0&c, &fjednorazowy&c.");
            return;
        }
        k.setCooldown(v);
        manager.saveKits();
        plugin.tell(s, "ustawiono", "&aUstawiono cooldown kitu &f%kit%&a: &f%wartosc%", "%kit%", k.getId(),
                "%wartosc%", KitManager.formatDuration(v));
    }

    private void setName(CommandSender s, String[] args) {
        if (args.length < 3) {
            usage(s, "/kit setname <nazwa> <tekst>");
            return;
        }
        Kit k = needKit(s, args[1]);
        if (k == null) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2) sb.append(' ');
            sb.append(args[i]);
        }
        String name = sb.toString();
        if (name.length() > 64) {
            plugin.tell(s, "zla-nazwa", "&cNazwa musi mieć od 1 do 64 znaków.");
            return;
        }
        k.setName(name);
        manager.saveKits();
        plugin.tell(s, "ustawiono", "&aUstawiono nazwę kitu &f%kit%&a: &r%wartosc%", "%kit%", k.getId(), "%wartosc%", name);
    }

    private void setRank(CommandSender s, String[] args) {
        if (args.length < 3) {
            usage(s, "/kit setrank <nazwa> <ranga>");
            return;
        }
        Kit k = needKit(s, args[1]);
        if (k == null) return;
        String rank = args[2].toLowerCase(Locale.ROOT);
        if (!manager.rankIds().contains(rank)) {
            plugin.tell(s, "zla-ranga", "&cNie ma takiej rangi. Dostępne: &f%lista%", "%lista%", String.join(", ", manager.rankIds()));
            return;
        }
        k.setRank(rank);
        manager.saveKits();
        plugin.tell(s, "ustawiono", "&aUstawiono rangę kitu &f%kit%&a: &r%wartosc%", "%kit%", k.getId(),
                "%wartosc%", manager.rankName(rank));
    }

    private void setItems(CommandSender s, String[] args) {
        if (!(s instanceof Player)) {
            plugin.tell(s, "tylko-gracz", "&cTa komenda jest tylko dla graczy.");
            return;
        }
        if (args.length < 2) {
            usage(s, "/kit setitems <nazwa>");
            return;
        }
        Kit k = needKit(s, args[1]);
        if (k == null) return;
        Player p = (Player) s;
        manager.copyFromInventory(p, k);
        plugin.tell(s, "ustawiono", "&aUstawiono zawartość kitu &f%kit%&a: &f%wartosc%", "%kit%", k.getId(),
                "%wartosc%", k.countItems() + " przedmiotów");
    }

    /* ------------------------------------------------------------ tab */

    private List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        String p = prefix.toLowerCase(Locale.ROOT);
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        }
        return out;
    }

    private List<String> kitIds() {
        List<String> ids = new ArrayList<>();
        for (Kit k : manager.all()) ids.add(k.getId());
        return ids;
    }

    private List<String> players() {
        List<String> names = new ArrayList<>();
        for (Player pl : Bukkit.getOnlinePlayers()) names.add(pl.getName());
        return names;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission(ADMIN);
        if (args.length == 1) {
            List<String> opts = kitIds();
            if (admin) opts.addAll(SUBS);
            return filter(opts, args[0]);
        }
        if (!admin) return new ArrayList<>();

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "delete":
                case "edit":
                case "give":
                case "setcooldown":
                case "setname":
                case "setrank":
                case "setitems":
                    return filter(kitIds(), args[1]);
                case "reset":
                    return filter(players(), args[1]);
                default:
                    return new ArrayList<>();
            }
        }
        if (args.length == 3) {
            switch (sub) {
                case "give":
                    return filter(players(), args[2]);
                case "reset":
                    return filter(kitIds(), args[2]);
                case "setrank":
                    return filter(manager.rankIds(), args[2]);
                case "setcooldown":
                    return filter(Arrays.asList("0", "30m", "1h", "12h", "1d", "7d", "jednorazowy"), args[2]);
                default:
                    return new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }
}
