package com.daynightwarfare.listeners;

import com.daynightwarfare.DayNightPlugin;
import com.daynightwarfare.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public class GameListener implements Listener {

    private final GameManager gameManager;

    public GameListener(DayNightPlugin plugin) {
        this.gameManager = plugin.getGameManager();
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (gameManager.isGracePeriodActive()) {
            if (event.getEntity() instanceof Player) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            Player damager = (Player) event.getDamager();
            Player victim = (Player) event.getEntity();
            if (gameManager.getPlayerManager().isAlive(damager) &&
                    gameManager.getPlayerManager().isAlive(victim) &&
                    gameManager.getTeamManager().getPlayerTeam(damager) == gameManager.getTeamManager().getPlayerTeam(victim)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (gameManager.getTeamManager().getPlayerTeam(player) != null) {
            event.getDrops().clear();
            event.setDroppedExp(0);

            gameManager.getPlayerManager().removePlayer(player);

            // Set to spectator mode after a short delay
            org.bukkit.Bukkit.getScheduler().runTaskLater(DayNightPlugin.getInstance(), () -> {
                player.setGameMode(GameMode.SPECTATOR);
                gameManager.checkWinCondition();
            }, 20L);

            event.deathMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize("<gray>" + player.getName() + "님이 사망했습니다.</gray>"));
        }
    }
}
