package com.daynightwarfare.skills.list;

import com.daynightwarfare.TeamType;
import com.daynightwarfare.skills.Skill;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;

public class SolarFlareSkill extends Skill {

    public SolarFlareSkill() {
        super(
                "solar-flare",
                "<gold>태양 섬광</gold>",
                Arrays.asList("<gray>우클릭하여 눈을 멀게 하는 빛을 방출합니다.</gray>"),
                TeamType.APOSTLE_OF_LIGHT,
                Material.GLOWSTONE_DUST,
                30L // Cooldown in seconds
        );
    }

    @Override
    public boolean execute(Player player) {
        for (Player targetPlayer : Bukkit.getOnlinePlayers()) {
            if (targetPlayer.equals(player)) continue;
            if (targetPlayer.getWorld() != player.getWorld() || targetPlayer.getLocation().distanceSquared(player.getLocation()) > 15 * 15) continue;

            TeamType targetTeam = gameManager.getTeamManager().getPlayerTeam(targetPlayer);
            if (targetTeam != null && targetTeam != getTeamType()) { // Enemy
                targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0));
                targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 70, 1));
            } else if (targetTeam == getTeamType()) { // Ally
                targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 0));
            }
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1f);
        return true;
    }
}
