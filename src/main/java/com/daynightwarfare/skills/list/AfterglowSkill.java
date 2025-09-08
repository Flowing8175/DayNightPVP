package com.daynightwarfare.skills.list;

import com.daynightwarfare.TeamType;
import com.daynightwarfare.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AfterglowSkill extends Skill implements Listener {

    private final Map<UUID, BukkitTask> afterglowTasks = new HashMap<>();
    private final Map<UUID, Location> lightBlockLocations = new HashMap<>();
    private final NamespacedKey afterglowDamageKey;

    public AfterglowSkill() {
        super(
                "afterglow",
                "<gold>잔광</gold>",
                Arrays.asList("<gray>우클릭하여 주변의 적을 불태웁니다.</gray>"),
                TeamType.APOSTLE_OF_LIGHT,
                Material.TORCH,
                45L
        );
        this.afterglowDamageKey = new NamespacedKey(plugin, "afterglow_damage");
    }

    @Override
    public boolean canUse(Player player) {
        if (!super.canUse(player)) {
            return false;
        }
        return true;
    }

    @Override
    public boolean execute(Player player) {
        if (afterglowTasks.containsKey(player.getUniqueId())) {
            afterglowTasks.get(player.getUniqueId()).cancel();
            removeLightBlock(player);
        }

        BukkitTask task = new BukkitRunnable() {
            int executions = 0;
            @Override
            public void run() {
                if (!player.isOnline() || executions >= 8) {
                    this.cancel();
                    removeLightBlock(player);
                    afterglowTasks.remove(player.getUniqueId());
                    return;
                }
                updateLightBlock(player);
                player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.01);

                for (Entity entity : player.getNearbyEntities(6, 6, 6)) {
                    if (entity instanceof LivingEntity && !entity.equals(player)) {
                        LivingEntity target = (LivingEntity) entity;
                        if (target instanceof Player) {
                            Player targetPlayer = (Player) target;
                            if (gameManager.getTeamManager().getPlayerTeam(targetPlayer) == TeamType.APOSTLE_OF_LIGHT) {
                                continue;
                            }
                        }
                        target.getPersistentDataContainer().set(afterglowDamageKey, PersistentDataType.BYTE, (byte) 1);
                        target.damage(1.0, player);
                    }
                }
                executions++;
            }
        }.runTaskTimer(plugin, 0L, 10L); // Runs every 0.5 seconds

        afterglowTasks.put(player.getUniqueId(), task);
        return true;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (target.getPersistentDataContainer().has(afterglowDamageKey, PersistentDataType.BYTE)) {
            event.setDamage(DamageModifier.ARMOR, 0);
            target.getPersistentDataContainer().remove(afterglowDamageKey);
        }
    }

    private void updateLightBlock(Player player) {
        removeLightBlock(player); // Remove previous light block
        Location newLoc = player.getLocation();
        if (newLoc.getBlock().isPassable()) {
            newLoc.getBlock().setType(Material.LIGHT);
            lightBlockLocations.put(player.getUniqueId(), newLoc);
        }
    }

    private void removeLightBlock(Player player) {
        Location oldLoc = lightBlockLocations.remove(player.getUniqueId());
        if (oldLoc != null && oldLoc.getBlock().getType() == Material.LIGHT) {
            oldLoc.getBlock().setType(Material.AIR);
        }
    }

    @Override
    public void cleanUp() {
        for (Location loc : lightBlockLocations.values()) {
            if (loc.getBlock().getType() == Material.LIGHT) {
                loc.getBlock().setType(Material.AIR);
            }
        }
        lightBlockLocations.clear();
        afterglowTasks.values().forEach(BukkitTask::cancel);
        afterglowTasks.clear();
    }
}
