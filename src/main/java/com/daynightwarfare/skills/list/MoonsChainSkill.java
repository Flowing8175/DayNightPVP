package com.daynightwarfare.skills.list;

import com.daynightwarfare.TeamType;
import com.daynightwarfare.skills.Skill;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;

public class MoonsChainSkill extends Skill {
    public MoonsChainSkill() {
        super(
                "moons-chain",
                "<aqua>달의 사슬</aqua>",
                Arrays.asList("<gray>우클릭하여 주변의 적을 속박합니다.</gray>"),
                TeamType.APOSTLE_OF_MOON,
                Material.FLOWER_BANNER_PATTERN,
                40L
        );
    }

    @Override
    public void execute(Player player) {
        for (Entity entity : player.getNearbyEntities(8, 8, 8)) {
            if (entity instanceof Player) {
                Player targetPlayer = (Player) entity;
                if (gameManager.getTeamManager().getPlayerTeam(targetPlayer) != TeamType.APOSTLE_OF_MOON) {
                    targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 2));
                    // The skill disabling logic will need to be handled in a central place,
                    // maybe in a new PlayerManager class as planned.
                    // For now, I'll leave a comment.
                }
            }
        }
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1f, 0.7f);
    }
}
