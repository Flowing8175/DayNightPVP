package com.daynightwarfare.listeners;

import com.daynightwarfare.DayNightPlugin;
import com.daynightwarfare.GameManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

public class GameListener implements Listener {

    private final GameManager gameManager;

    public GameListener(DayNightPlugin plugin) {
        this.gameManager = plugin.getGameManager();
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (gameManager.isGracePeriodActive()) {
            event.setCancelled(true);
        }
    }
}
