package pl.firestar.kits;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    private KitManager manager;
    private GuiManager gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new KitManager(this);
        manager.load();
        gui = new GuiManager(this, manager);

        getServer().getPluginManager().registerEvents(new GuiListener(this, manager, gui), this);

        PluginCommand pc = getCommand("kit");
        if (pc == null) {
            getLogger().severe("Nie znaleziono komendy /kit w plugin.yml!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        KitCommand cmd = new KitCommand(this, manager, gui);
        pc.setExecutor(cmd);
        pc.setTabCompleter(cmd);

        long period = 20L * 60L; // autozapis cooldownow co minute
        getServer().getScheduler().runTaskTimer(this, () -> manager.saveClaimsIfDirty(), period, period);
        // odswiezanie odliczania cooldownu w otwartych menu
        getServer().getScheduler().runTaskTimer(this, () -> gui.tickMenus(), 20L, 20L);
        getLogger().info("FirestarKits wlaczony. Wczytano kitow: " + manager.all().size());
    }

    @Override
    public void onDisable() {
        if (gui != null) gui.flushOpenEditors();
        if (manager != null) manager.saveAll();
    }

    public void reloadAll() {
        if (gui != null) gui.flushOpenEditors();
        manager.saveAll();
        reloadConfig();
        manager.load();
    }

    public String color(String s) {
        return Items.color(s);
    }

    public String text(String path, String def) {
        return color(getConfig().getString(path, def));
    }

    /** Wysyla wiadomosc z prefixem. pairs = "%klucz%", "wartosc", ... */
    public void tell(CommandSender to, String key, String def, String... pairs) {
        String text = getConfig().getString("wiadomosci." + key, def);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace(pairs[i], pairs[i + 1]);
        }
        to.sendMessage(color(getConfig().getString("prefix", "&d&lKITY &8» ")) + color(text));
    }

    public void sound(Player p, String key, float pitch) {
        try {
            p.playSound(p.getLocation(), key, 0.8f, pitch);
        } catch (Exception ignored) {
            // dzwiek nie jest krytyczny
        }
    }

    public void actionBar(Player p, String text) {
        try {
            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(color(text)));
        } catch (Throwable ignored) {
            // actionbar nie jest krytyczny
        }
    }

    public static void runLater(Main plugin, Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }
}
