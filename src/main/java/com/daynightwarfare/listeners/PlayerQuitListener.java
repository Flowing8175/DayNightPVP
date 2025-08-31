package com.daynightwarfare.listeners;

import com.daynightwarfare.DayNightPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    public PlayerQuitListener(DayNightPlugin plugin) {
        // In a more complex system, we might store player data here.
        // For this implementation, the join listener will handle reconnects.
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // No action needed on quit based on current requirements.
        // The player's data (team, alive status) remains in GameManager.
    }
}
