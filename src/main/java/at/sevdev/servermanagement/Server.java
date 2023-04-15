package at.sevdev.servermanagement;

import net.md_5.bungee.api.ProxyServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class Server {

    private final String name;
    private final String startScript;
    private final String stopScript;
    private final WeakReference<ServerManagement> serverManagement;
    private int maxIdleTime = 10;
    private Status status = Status.OFFLINE;
    private Timer timer;
    private LocalDateTime lastStarted;

    public Server(ServerManagement serverManagement, String name, String startScript, String stopScript) {
        this.serverManagement = new WeakReference<>(serverManagement);
        this.name = name;
        this.startScript = startScript;
        this.stopScript = stopScript;
    }

    public String getName() {
        return name;
    }

    public String getStartScript() {
        return startScript;
    }

    public String getStopScript() {
        return stopScript;
    }

    public int getMaxIdleTime() {
        return maxIdleTime;
    }

    public void setMaxIdleTime(int maxIdleTime) {
        this.maxIdleTime = maxIdleTime;
    }

    public Status getStatus() {
        return status;
    }

    public Server setStatus(Status status) {
        if (this.status != status) {
            serverManagement.get().getLogger().info("Server (" + getName() + ") changed to " + status.name());
        }
        if (status == Status.ONLINE) {
            setTimer();
        }
        this.status = status;
        return this;
    }

    public LocalDateTime getLastStarted() {
        if (lastStarted == null) {
            return LocalDateTime.now();
        }
        return lastStarted;
    }

    public void setLastStarted(LocalDateTime lastStarted) {
        this.lastStarted = lastStarted;
    }

    public Server setTimer() {
        if (timer != null) {
            timer.cancel();
        }
        timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (ProxyServer.getInstance().getServerInfo(getName()).getPlayers().size() == 0 && getStatus() == Status.ONLINE) {
                    serverManagement.get().getLogger().info("Server (" + getName() + ") was idle for longer than " + getMaxIdleTime() + " minutes, shutting down!");
                    try {
                        Process process = new ProcessBuilder().command(List.of(("bash " + getStopScript()).split(" "))).start();
                        String log = new BufferedReader(new InputStreamReader(process.getInputStream())).lines()
                                .collect(Collectors.joining("\n"));
                        serverManagement.get().getLogger().info(log);
                        int exitVal = process.waitFor();
                        if (exitVal == 0) {
                            setStatus(Status.OFFLINE);
                        } else {
                            serverManagement.get().getLogger().info("Server (" + getName() + ") StopScript Exit Value: " + exitVal);
                        }
                    } catch (IOException | InterruptedException e) {
                        serverManagement.get().getLogger().log(Level.SEVERE, e.getMessage(), e);
                    }
                }
            }
        }, TimeUnit.MINUTES.toMillis(getMaxIdleTime()) + (getStatus() == Status.STARTING ? serverManagement.get().serverStartupTime.toMillis() : 0));
        return this;
    }

    public boolean isFilled() {
        return name != null && !name.isEmpty() && startScript != null && !startScript.isEmpty() && stopScript != null && !stopScript.isEmpty();
    }

    @Override
    public String toString() {
        return "Server{" +
                "name='" + name + '\'' +
                ", maxIdleTime=" + maxIdleTime +
                ", status=" + status +
                '}';
    }

    public enum Status {
        OFFLINE, STARTING, ONLINE, ERROR
    }
}
