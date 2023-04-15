package at.sevdev.servermanagement.listener;

import at.sevdev.servermanagement.Server;
import at.sevdev.servermanagement.ServerManagement;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.event.ServerDisconnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.lang.ref.WeakReference;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public class Events implements Listener {

    private final static int PROTOCOL = 587;
    private final WeakReference<ServerManagement> serverManagement;

    public Events(ServerManagement serverManagement) {
        this.serverManagement = new WeakReference<>(serverManagement);
    }

    @EventHandler
    public void onProxyPing(ProxyPingEvent event) {
        Optional<Server> optionalServer = getServer(event.getConnection());
        if (optionalServer.isPresent()) {
            Server server = optionalServer.get();
            if (server.getStatus() != Server.Status.ONLINE) {
                event.getResponse().setPlayers(new ServerPing.Players(0, 0, null));
                if (server.getStatus() == Server.Status.STARTING) {
                    long seconds = LocalDateTime.now().until(server.getLastStarted().plus(serverManagement.get().serverStartupTime), ChronoUnit.SECONDS) + 1;
                    event.getResponse().setDescriptionComponent(
                            new ComponentBuilder("This server is starting. Please wait.\nETA: " + seconds + "s")
                                    .color(ChatColor.AQUA)
                                    .create()[0]);
                    event.getResponse().setVersion(new ServerPing.Protocol("● Starting", PROTOCOL));
                } else {
                    event.getResponse().setDescriptionComponent(
                            new ComponentBuilder("This server is offline.")
                                    .color(ChatColor.DARK_RED)
                                    .create()[0]);
                    event.getResponse().setVersion(new ServerPing.Protocol("● Offline", PROTOCOL));
                }
            }
        }
    }

    @EventHandler
    public void onPreLogin(PreLoginEvent event) {
        Optional<Server> optionalServer = getServer(event.getConnection());
        if (optionalServer.isPresent()) {
            Server server = optionalServer.get();

            if (server.getStatus() != Server.Status.ONLINE) {
                String message = serverManagement.get().startMCServer(server);

                event.setCancelled(true);
                if (server.getStatus() == Server.Status.ERROR) {
                    event.setCancelReason(
                            new ComponentBuilder("Hi ")
                                    .append(event.getConnection().getName()).color(ChatColor.GREEN)
                                    .append("!").color(ChatColor.WHITE)
                                    .append("\n\n")
                                    .append("Start error" + (!message.isBlank() ? ": " + message : "")).color(ChatColor.DARK_RED)
                                    .create()
                    );
                } else {
                    long seconds = LocalDateTime.now().until(server.getLastStarted().plus(serverManagement.get().serverStartupTime), ChronoUnit.SECONDS) + 1;
                    event.setCancelReason(new ComponentBuilder("Hi ")
                            .append(event.getConnection().getName()).color(ChatColor.GREEN)
                            .append("!").color(ChatColor.WHITE)
                            .append("\n\n")
                            .append("Server is starting. Please wait.\nETA: " + seconds + "s").color(ChatColor.AQUA)
                            .create()
                    );
                }
            }
        }
    }

    @EventHandler
    public void onServerDisconnect(ServerDisconnectEvent event) {
        Optional<Server> optionalServer = getServer(event.getPlayer().getPendingConnection());
        if (optionalServer.isPresent()) {
            Server server = optionalServer.get();
            if (server.getStatus() == Server.Status.ONLINE && event.getTarget().getPlayers().size() == 0) {
                serverManagement.get().getLogger().info("Last player left server (" + server.getName() + ")");
                server.setTimer();
            }
        }
    }

    private Optional<Server> getServer(PendingConnection pendingConnection) {
        return serverManagement.get().getServerList().stream()
                .filter(server -> pendingConnection.getListener().getServerPriority().contains(server.getName()))
                .findAny();
    }

}
