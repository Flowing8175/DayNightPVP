package com.daynightwarfare.managers;

import com.daynightwarfare.DayNightPlugin;
import com.daynightwarfare.GameManager;
import com.daynightwarfare.TeamType;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import com.daynightwarfare.GameState;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class PlayerManager {
    private final DayNightPlugin plugin;
    private final GameManager gameManager;
    private final TeamManager teamManager;
    private final Set<UUID> alivePlayers = new HashSet<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public PlayerManager(DayNightPlugin plugin, GameManager gameManager, TeamManager teamManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.teamManager = teamManager;
    }

    public void addPlayer(Player player) {
        alivePlayers.add(player.getUniqueId());
    }

    public void removePlayer(Player player) {
        alivePlayers.remove(player.getUniqueId());
    }

    public boolean isAlive(Player player) {
        return alivePlayers.contains(player.getUniqueId());
    }

    public Set<UUID> getAlivePlayers() {
        return alivePlayers;
    }

    public void clearPlayers() {
        alivePlayers.clear();
    }

    public boolean wasPlayerInGame(Player player) {
        return teamManager.getPlayerTeam(player) != null;
    }

    public void handleReconnect(Player player) {
        teamManager.updatePlayerDisplayName(player);
        if (!isAlive(player)) {
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage(miniMessage.deserialize("<gray>관전자로 다시 참여했습니다.</gray>"));
        } else {
            player.sendMessage(miniMessage.deserialize("<green>게임에 다시 참여했습니다!</green>"));
        }
    }

    public void handleLateJoin(Player player) {
        if (gameManager.getState() == GameState.IN_GAME) {
            player.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
            player.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            player.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
            player.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));
            player.getInventory().addItem(new ItemStack(Material.STONE_PICKAXE));
            player.getInventory().addItem(new ItemStack(Material.IRON_AXE));
        }

        long lightTeamCount = teamManager.getPlayerTeams().values().stream().filter(t -> t == TeamType.APOSTLE_OF_LIGHT).count();
        long moonTeamCount = teamManager.getPlayerTeams().values().stream().filter(t -> t == TeamType.APOSTLE_OF_MOON).count();
        TeamType assignedTeam = (lightTeamCount <= moonTeamCount) ? TeamType.APOSTLE_OF_LIGHT : TeamType.APOSTLE_OF_MOON;

        teamManager.setPlayerTeam(player, assignedTeam);
        addPlayer(player);

        gameManager.supplySkillItems(player);

        gameManager.teleportPlayerToTeamSpawn(player);

        player.sendMessage(miniMessage.deserialize("<green>진행중인 게임에 참여합니다!</green>"));
        player.sendMessage(miniMessage.deserialize(
                "<gray>당신은 <team> 팀입니다.</gray>",
                Placeholder.component("team", assignedTeam.getStyledDisplayName())
        ));
    }
}
