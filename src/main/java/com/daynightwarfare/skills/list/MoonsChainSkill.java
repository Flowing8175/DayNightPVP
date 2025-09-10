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
                20L
        );
    }

    @Override
    public boolean execute(Player player) {
        for (Entity entity : player.getNearbyEntities(8, 8, 8)) {
            if (entity instanceof Player) {
                Player targetPlayer = (Player) entity;
                if (gameManager.getTeamManager().getPlayerTeam(targetPlayer) != TeamType.APOSTLE_OF_MOON) {
                    targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 19));
                }
            }
        }
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1f, 0.7f);
        return true;
    }
}
