package tk.bungeefan.servermanagement.commands;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.plugin.Command;
import tk.bungeefan.servermanagement.ServerManagement;

import java.lang.ref.WeakReference;
import java.util.logging.Level;

public class PluginCommand extends Command {

    private final WeakReference<ServerManagement> serverManagement;

    public PluginCommand(ServerManagement serverManagement) {
        super("smanage");
        this.serverManagement = new WeakReference<>(serverManagement);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length >= 1) {
            if (args[0].equalsIgnoreCase("reload")) {
                sender.sendMessage(new ComponentBuilder("Config reloading...").color(ChatColor.RED).create());
                try {
                    serverManagement.get().loadConfig();
                    sender.sendMessage(new ComponentBuilder("Config reloaded!").color(ChatColor.DARK_GREEN).create());
                } catch (Exception e) {
                    serverManagement.get().getLogger().log(Level.SEVERE, "Error when loading ConfigFile", e);
                    sender.sendMessage(new ComponentBuilder("Error when reloading config!").color(ChatColor.DARK_RED).create());
                }
            }
        }
    }
}
