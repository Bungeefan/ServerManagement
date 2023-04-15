package at.sevdev.servermanagement;

import at.sevdev.servermanagement.commands.PluginCommand;
import at.sevdev.servermanagement.listener.Events;
import com.google.gson.internal.LinkedTreeMap;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.JsonConfiguration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ServerManagement extends Plugin {

    private final List<Server> serverList = new ArrayList<>();
    private final String configName = "config.json";
    private final Path configPath = getDataFolder().toPath().resolve(configName);
    public Duration serverStartupTime = Duration.ofSeconds(20);

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }
        if (!Files.exists(configPath)) {
            try (InputStream in = getResourceAsStream(configName)) {
                Files.copy(in, configPath);
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Error when copying default config file", e);
            }
        }
        try {
            loadConfig();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error when loading ConfigFile " + configPath.getFileName(), e);
        }

        getProxy().getPluginManager().registerCommand(this, new PluginCommand(this));
        getProxy().getPluginManager().registerListener(this, new Events(this));
        getLogger().info("Loaded");
    }

    public void loadConfig() throws Exception {
        if (Files.exists(configPath)) {
            Configuration configuration = ConfigurationProvider.getProvider(JsonConfiguration.class).load(configPath.toFile());
            List<LinkedTreeMap<String, Object>> list = (List<LinkedTreeMap<String, Object>>) configuration.getList("server", new ArrayList<LinkedTreeMap<String, Object>>());

            int startupTime = configuration.getInt("startupTime");
            if (startupTime > 0) {
                this.serverStartupTime = Duration.ofSeconds(startupTime);
            }
            getLogger().info("Using " + this.serverStartupTime.toSeconds() + "s as StartupTime");
            serverList.clear();
            serverList.addAll(list.stream().map(serverConf -> {
                Server s = new Server(this,
                        (String) serverConf.get("name"),
                        (String) serverConf.get("startScript"),
                        (String) serverConf.get("stopScript")
                );
                if (serverConf.containsKey("maxIdleTime")) {
                    s.setMaxIdleTime(((Double) serverConf.get("maxIdleTime")).intValue());
                }
                Path startScript = Path.of(s.getStartScript().split(" ")[0]);
                if (!Files.exists(startScript)) {
                    getLogger().warning("StartScript of Server (" + s.getName() + ") doesn't exist!");
                }
                Path stopScript = Path.of(s.getStopScript().split(" ")[0]);
                if (!Files.exists(stopScript)) {
                    getLogger().warning("StopScript of Server (" + s.getName() + ") doesn't exist!");
                }
                if (!s.isFilled()) {
                    getLogger().warning("Config for Server (" + s.getName() + ") is incomplete, can't start!");
                }
                return s;
            }).collect(Collectors.toList()));

            serverList.removeIf(server -> !server.isFilled());

            serverList.stream()
                    .filter(server -> server.getStatus() == Server.Status.OFFLINE)
                    .forEach(this::updateScreenStatus);
            if (serverList.isEmpty()) {
                getLogger().warning("ConfigFile " + configPath.getFileName() + " is empty!");
            } else {
                getLogger().info("Config loaded!");
                getLogger().info("Registered Server: " + serverList);
            }
        } else {
            getLogger().severe("ConfigFile " + configPath.getFileName() + " doesn't exist!");
        }
    }

    public List<Server> getServerList() {
        return serverList;
    }

    private void updateScreenStatus(Server server) {
        try (Socket s = new Socket()) {
            s.setSoTimeout(1000);
            s.connect(ProxyServer.getInstance().getServerInfo(server.getName()).getSocketAddress());
            server.setStatus(Server.Status.ONLINE);
        } catch (IOException ignored) {
            server.setStatus(Server.Status.OFFLINE);
        }
    }

    public String startMCServer(Server s) {
        if (s.getStatus() == Server.Status.ONLINE || s.getStatus() == Server.Status.STARTING) {
            return null;
        }
        getLogger().info("Starting server " + s.getName() + "!");
        try {
            Process process = new ProcessBuilder().command(List.of(("bash " + s.getStartScript()).split(" "))).start();
            String log = new BufferedReader(new InputStreamReader(process.getInputStream())).lines()
                    .collect(Collectors.joining("\n"));
            getLogger().info(log);
            int exitVal = process.waitFor();
            if (exitVal == 0) {
                s.setLastStarted(LocalDateTime.now());
                s.setStatus(Server.Status.STARTING);
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        for (int i = 0; i < 5; i++) {
                            updateScreenStatus(s);
                            if (s.getStatus() == Server.Status.ONLINE) {
                                break;
                            }
                            try {
                                Thread.sleep(TimeUnit.SECONDS.toMillis(10));
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }, serverStartupTime.toMillis());
            } else {
                getLogger().info("Server (" + s.getName() + ") StartScript Exit Value: " + exitVal);
                s.setStatus(Server.Status.ERROR);
            }
            return log;
        } catch (IOException | InterruptedException e) {
            s.setStatus(Server.Status.ERROR);
            getLogger().warning(e.getMessage());
            return e.getMessage();
        }
    }
}
