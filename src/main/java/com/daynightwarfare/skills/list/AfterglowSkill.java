package com.daynightwarfare.skills.list;

import com.daynightwarfare.TeamType;
import com.daynightwarfare.skills.Skill;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AfterglowSkill extends Skill {

    private final Map<UUID, BukkitTask> afterglowTasks = new HashMap<>();

    public AfterglowSkill() {
        super(
                "afterglow",
                "<gold>잔광</gold>",
                Arrays.asList("<gray>우클릭하여 주변의 적을 불태웁니다.</gray>"),
                TeamType.APOSTLE_OF_LIGHT,
                Material.TORCH,
                45L
        );
    }

    @Override
    public void execute(Player player) {
        if (afterglowTasks.containsKey(player.getUniqueId())) {
            afterglowTasks.get(player.getUniqueId()).cancel();
        }

        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 10) {
                    this.cancel();
                    afterglowTasks.remove(player.getUniqueId());
                    return;
                }
                player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.01);

                for (Entity entity : player.getNearbyEntities(6, 6, 6)) {
                    if (entity instanceof Player) {
                        Player targetPlayer = (Player) entity;
                        if (gameManager.getTeamManager().getPlayerTeam(targetPlayer) != TeamType.APOSTLE_OF_LIGHT) {
                            if (targetPlayer instanceof LivingEntity) {
                                ((LivingEntity) targetPlayer).damage(1.0, player);
                            }
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 10L);

        afterglowTasks.put(player.getUniqueId(), task);
    }
}
