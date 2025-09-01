package com.daynightwarfare.managers;

import com.daynightwarfare.DayNightPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

public class TimeManager {
    private final DayNightPlugin plugin;
    private final double timeMultiplier;
    private boolean announcedSunrise = false;
    private boolean announcedSunset = false;

    public TimeManager(DayNightPlugin plugin) {
        this.plugin = plugin;
        this.timeMultiplier = plugin.getConfig().getDouble("day-night-cycle-multiplier", 1.0);
        if (this.timeMultiplier != 1.0) {
            start();
        }
    }

    private void start() {
        World world = Bukkit.getWorlds().get(0);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);

        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = world.getTime();
                long timeToAdd = (long) timeMultiplier;

                if ((currentTime >= 12200 && currentTime < 13800) || (currentTime >= 22000)) {
                    timeToAdd *= 10;
                }

                world.setTime(currentTime + timeToAdd);
                handleAnnouncements(world.getTime());
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void handleAnnouncements(long currentTime) {
        // Announce sunset
        if (currentTime >= 11900 && currentTime < 12000 && !announcedSunset) {
            Bukkit.broadcast(MiniMessage.miniMessage().deserialize("<gold>태양이 저물기 시작합니다...</gold>"));
            announcedSunset = true;
        } else if (currentTime >= 12000) {
            announcedSunset = false; // Reset for the next day
        }

        // Announce sunrise
        if (currentTime >= 23900 && !announcedSunrise) {
            Bukkit.broadcast(MiniMessage.miniMessage().deserialize("<aqua>달이 기울고 새벽이 다가옵니다...</aqua>"));
            announcedSunrise = true;
        } else if (currentTime < 23900 && currentTime > 1000) { // Reset after sunrise is over
            announcedSunrise = false;
        }
    }

    public void restoreDefaults() {
        if (this.timeMultiplier != 1.0) {
            Bukkit.getWorlds().get(0).setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        }
    }
}
