package com.daynightwarfare.skills.list;

import com.daynightwarfare.TeamType;
import com.daynightwarfare.skills.Skill;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ShadowWingsSkill extends Skill {

    private enum SkillState {
        WAITING_FOR_TAKEOFF,
        GLIDING,
        DROPPING
    }

    private final Map<UUID, ItemStack> originalChestplates = new HashMap<>();
    private final Map<UUID, SkillState> playerSkillStates = new HashMap<>();
    private final Map<UUID, BukkitTask> timeoutTasks = new HashMap<>();
    private final Set<UUID> fallImmunityPlayers = new HashSet<>();

    public ShadowWingsSkill() {
        super(
                "shadow-wings",
                "<aqua>그림자 날개</aqua>",
                Arrays.asList("<gray>우클릭하여 날개를 펼쳐 일시적으로 비행합니다.</gray>"),
                TeamType.APOSTLE_OF_MOON,
                Material.FEATHER,
                60L
        );
    }

    @Override
    public boolean execute(Player player) {
        UUID uuid = player.getUniqueId();
        removeElytra(player, false); // Clear previous state

        originalChestplates.put(uuid, player.getInventory().getChestplate());
        playerSkillStates.put(uuid, SkillState.WAITING_FOR_TAKEOFF);

        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta meta = elytra.getItemMeta();
        meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
        if (meta instanceof Damageable) {
            byte lightLevel = player.getLocation().getBlock().getLightLevel();
            if (lightLevel >= 7) {
                ((Damageable) meta).setDamage(elytra.getType().getMaxDurability() - 2);
                player.sendMessage(miniMessage.deserialize("<red>어둠의 힘이 부족하여 날개가 불완전합니다.</red>"));
            } else {
                ((Damageable) meta).setDamage(elytra.getType().getMaxDurability() - 4);
            }
        }
        elytra.setItemMeta(meta);
        player.getInventory().setChestplate(elytra);

        ItemStack firework = new ItemStack(Material.FIREWORK_ROCKET);
        FireworkMeta fwMeta = (FireworkMeta) firework.getItemMeta();
        fwMeta.setPower(1);
        firework.setItemMeta(fwMeta);
        player.getInventory().addItem(firework);

        BukkitTask timeoutTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> removeElytra(player, true), 200L);
        timeoutTasks.put(uuid, timeoutTask);

        return true;
    }

    private void removeElytra(Player player, boolean grantLingeringImmunity) {
        UUID uuid = player.getUniqueId();
        if (!originalChestplates.containsKey(uuid)) return;

        BukkitTask timeoutTask = timeoutTasks.remove(uuid);
        if (timeoutTask != null) timeoutTask.cancel();

        if (gameManager.isGameInProgress()) {
            setCooldown(player);
        }

        player.getInventory().setChestplate(originalChestplates.remove(uuid));
        player.getInventory().remove(Material.FIREWORK_ROCKET);
        playerSkillStates.remove(uuid);

        if (grantLingeringImmunity) {
            fallImmunityPlayers.add(uuid);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> fallImmunityPlayers.remove(uuid), 60L); // 3 seconds
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!originalChestplates.containsKey(uuid)) return;

        // Liquid check
        Block blockIn = player.getLocation().getBlock();
        if (blockIn.isLiquid()) {
            if (blockIn.getType() == Material.WATER) {
                player.sendMessage(miniMessage.deserialize("<blue>날개가 물에 젖어 펼칠 수 없게 되었습니다.</blue>"));
            } else if (blockIn.getType() == Material.LAVA) {
                player.sendMessage(miniMessage.deserialize("<red>날개가 불에 타 버렸습니다.</red>"));
            }
            removeElytra(player, true);
            return;
        }

        SkillState state = playerSkillStates.get(uuid);
        if (state == null) return;

        switch (state) {
            case WAITING_FOR_TAKEOFF:
                if (player.isGliding()) {
                    playerSkillStates.put(uuid, SkillState.GLIDING);
                }
                break;
            case GLIDING:
                if (player.isOnGround()) {
                    removeElytra(player, true);
                }
                break;
            case DROPPING:
                if (player.isOnGround()) {
                    // Perform smash
                    player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.5f, 0.5f);
                    player.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, player.getLocation(), 800, 2, 0.5, 2, 1, org.bukkit.Bukkit.createBlockData(Material.GRASS_BLOCK));

                    for (Entity entity : player.getNearbyEntities(5, 3, 5)) {
                        if (entity instanceof LivingEntity && !entity.equals(player)) {
                            LivingEntity target = (LivingEntity) entity;
                            if (player.getLocation().distance(target.getLocation()) > 8) continue;

                            if (target instanceof Player && gameManager.getTeamManager().getPlayerTeam((Player) target) == TeamType.APOSTLE_OF_MOON) {
                                continue;
                            }

                            double damage = player.getFallDistance() * 0.5 - player.getLocation().distance(target.getLocation()) * 0.5;
                            if (damage > 0) {
                                target.damage(Math.min(damage, 8.0), player);
                            }

                            Vector knockback = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.5);
                            target.setVelocity(knockback);
                        }
                    }
                    // Smash is a final landing, so no lingering immunity needed from removeElytra
                    removeElytra(player, false);
                    // Grant temporary immunity for this landing tick
                    fallImmunityPlayers.add(uuid);
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> fallImmunityPlayers.remove(uuid), 1L);
                }
                break;
        }
    }

    @EventHandler
    public void onPlayerItemBreak(PlayerItemBreakEvent event) {
        Player player = event.getPlayer();
        if (originalChestplates.containsKey(player.getUniqueId()) && event.getBrokenItem().getType() == Material.ELYTRA) {
            removeElytra(player, true);
        }
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        SkillState state = playerSkillStates.get(uuid);

        if (event.isSneaking() && state == SkillState.GLIDING) {
            playerSkillStates.put(uuid, SkillState.DROPPING);
            if (player.isGliding()) {
                player.setGliding(false);
            }
            player.setVelocity(new Vector(0, -10, 0));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (fallImmunityPlayers.contains(uuid) || playerSkillStates.containsKey(uuid)) {
                event.setCancelled(true);
            }
        }
    }

    @Override
    public void cleanUp() {
        for (UUID uuid : new HashSet<>(originalChestplates.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                removeElytra(player, false);
            }
        }
        originalChestplates.clear();
        playerSkillStates.clear();
        timeoutTasks.values().forEach(BukkitTask::cancel);
        timeoutTasks.clear();
        fallImmunityPlayers.clear();
    }
}
