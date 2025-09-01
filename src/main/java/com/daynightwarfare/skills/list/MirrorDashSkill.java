package com.daynightwarfare.skills.list;

import com.daynightwarfare.TeamType;
import com.daynightwarfare.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Arrays;

public class MirrorDashSkill extends Skill {
    public MirrorDashSkill() {
        super(
                "mirror-dash",
                "<gold>거울 잔상</gold>",
                Arrays.asList("<gray>우클릭하여 적의 뒤로 돌진합니다.</gray>"),
                TeamType.APOSTLE_OF_LIGHT,
                Material.GLASS_PANE,
                25L
        );
    }

    @Override
    public void execute(Player player) {
        Vector playerDirection = player.getEyeLocation().getDirection();
        playerDirection.setY(0).normalize();

        Player bestTarget = null;
        double closestDot = -1.0;
        double coneThreshold = Math.cos(Math.toRadians(22.5));

        for (Player targetPlayer : player.getWorld().getPlayers()) {
            if (targetPlayer.equals(player) || gameManager.getTeamManager().getPlayerTeam(targetPlayer) == TeamType.APOSTLE_OF_LIGHT) {
                continue;
            }
            if (targetPlayer.getLocation().distanceSquared(player.getLocation()) > 30 * 30) {
                continue;
            }

            Vector targetDirection = targetPlayer.getLocation().toVector().subtract(player.getLocation().toVector());
            targetDirection.setY(0).normalize();

            double dot = playerDirection.dot(targetDirection);

            if (dot > coneThreshold && dot > closestDot) {
                closestDot = dot;
                bestTarget = targetPlayer;
            }
        }

        if (bestTarget != null) {
            Vector targetLookDir = bestTarget.getLocation().getDirection().normalize();
            Location behind = bestTarget.getLocation().subtract(targetLookDir.multiply(3));

            if (behind.getBlock().isPassable() && behind.clone().add(0, 1, 0).getBlock().isPassable()) {
                player.teleport(behind);
            } else {
                player.sendMessage(miniMessage.deserialize("<red>대상을 추적할 수 없습니다.</red>"));
            }
        } else {
            player.sendMessage(miniMessage.deserialize("<red>시야에 대상이 없습니다.</red>"));
        }
    }
}
