package com.daynightwarfare.skills.list;

import com.daynightwarfare.TeamType;
import com.daynightwarfare.skills.Skill;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ShadowWingsSkill extends Skill {

    private final Map<UUID, ItemStack> originalChestplates = new HashMap<>();
    private final Map<UUID, Boolean> fireworkUsed = new HashMap<>();
    private final Map<UUID, Boolean> elytraBroken = new HashMap<>();
    private final Map<UUID, Boolean> moonSmashUsed = new HashMap<>();
    private final Map<UUID, BukkitTask> timeoutTasks = new HashMap<>();
    private final Set<UUID> fallImmunityPlayers = new HashSet<>();

    private final NamespacedKey fireworkKey;
    private final NamespacedKey moonSmashKey;

    public ShadowWingsSkill() {
        super(
                "shadow-wings",
                "<aqua>그림자 날개</aqua>",
                Arrays.asList("<gray>우클릭하여 일시적으로 비행합니다.</gray>"),
                TeamType.APOSTLE_OF_MOON,
                Material.FEATHER,
                60L
        );
        this.fireworkKey = new NamespacedKey(plugin, "shadow_firework");
        this.moonSmashKey = new NamespacedKey(plugin, "moon_smash_active");
    }

    @Override
    public boolean execute(Player player) {
        UUID uuid = player.getUniqueId();
        removeElytra(player, false); // Clear previous state without granting immunity

        originalChestplates.put(uuid, player.getInventory().getChestplate());
        fireworkUsed.put(uuid, false);
        elytraBroken.put(uuid, false);
        moonSmashUsed.put(uuid, false);

        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta meta = elytra.getItemMeta();
        meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
        if (meta instanceof Damageable) {
            byte lightLevel = player.getLocation().getBlock().getLightLevel();
            if (lightLevel >= 7) {
                ((Damageable) meta).setDamage(elytra.getType().getMaxDurability() - 2);
                player.sendMessage("§c어둠의 힘이 부족하여 날개가 불완전합니다.");
            } else {
                ((Damageable) meta).setDamage(elytra.getType().getMaxDurability() - 4);
            }
        }
        elytra.setItemMeta(meta);
        player.getInventory().setChestplate(elytra);

        ItemStack firework = new ItemStack(Material.FIREWORK_ROCKET);
        ItemMeta fireworkMeta = firework.getItemMeta();
        fireworkMeta.getPersistentDataContainer().set(fireworkKey, PersistentDataType.BYTE, (byte) 1);
        firework.setItemMeta(fireworkMeta);
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

        player.getInventory().setChestplate(originalChestplates.remove(uuid));

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.hasItemMeta()) {
                if (item.getItemMeta().getPersistentDataContainer().has(fireworkKey, PersistentDataType.BYTE)) {
                    player.getInventory().remove(item);
                    break;
                }
            }
        }

        fireworkUsed.remove(uuid);
        elytraBroken.remove(uuid);
        moonSmashUsed.remove(uuid);

        if (grantLingeringImmunity) {
            fallImmunityPlayers.add(uuid);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> fallImmunityPlayers.remove(uuid), 60L); // 3 seconds
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!originalChestplates.containsKey(player.getUniqueId())) return;

        if (player.isGliding() && event.getAction().isRightClick()) {
            ItemStack item = event.getItem();
            if (item != null && item.getType() == Material.FIREWORK_ROCKET && item.hasItemMeta()) {
                if (item.getItemMeta().getPersistentDataContainer().has(fireworkKey, PersistentDataType.BYTE)) {
                    fireworkUsed.put(player.getUniqueId(), true);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!originalChestplates.containsKey(uuid)) return;

        boolean usedFirework = fireworkUsed.getOrDefault(uuid, false);
        boolean isBroken = elytraBroken.getOrDefault(uuid, false);

        if (player.isOnGround() && (usedFirework || isBroken)) {
            removeElytra(player, true);
        }
    }

    @EventHandler
    public void onPlayerItemBreak(PlayerItemBreakEvent event) {
        Player player = event.getPlayer();
        if (originalChestplates.containsKey(player.getUniqueId()) && event.getBrokenItem().getType() == Material.ELYTRA) {
            elytraBroken.put(player.getUniqueId(), true);
        }
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        boolean isEligible = originalChestplates.containsKey(uuid) || fallImmunityPlayers.contains(uuid);
        boolean hasUsedSmash = moonSmashUsed.getOrDefault(uuid, true);

        if (!event.isSneaking() || player.isOnGround() || !isEligible || hasUsedSmash) {
            return;
        }

        player.getPersistentDataContainer().set(moonSmashKey, PersistentDataType.BYTE, (byte) 1);
        if (player.isGliding()) {
            player.setGliding(false);
        }
        player.setVelocity(new Vector(0, -20, 0));
        moonSmashUsed.put(uuid, true);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (player.getPersistentDataContainer().has(moonSmashKey, PersistentDataType.BYTE)) {
                event.setCancelled(true);
                player.getPersistentDataContainer().remove(moonSmashKey);

                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.5f, 0.5f);
                player.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, player.getLocation(), 3000, 2, 0.5, 2, 1, org.bukkit.Bukkit.createBlockData(Material.GRASS_BLOCK));

                for (Entity entity : player.getNearbyEntities(8, 8, 8)) {
                    if (entity instanceof LivingEntity && !entity.equals(player)) {
                        LivingEntity target = (LivingEntity) entity;

                        if (player.getLocation().distance(target.getLocation()) > 8) {
                            continue;
                        }

                        if (target instanceof Player) {
                            if (gameManager.getTeamManager().getPlayerTeam((Player) target) == TeamType.APOSTLE_OF_MOON) {
                                continue;
                            }
                        }

                        double damage = player.getFallDistance() * 0.4 - player.getLocation().distance(target.getLocation()) * 1.5;
                        if (damage > 0) {
                            target.damage(damage, player);
                        }

                        Vector knockback = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.5);
                        target.setVelocity(knockback);
                    }
                }
                // Smash is a final landing, so no lingering immunity
                removeElytra(player, false);
            } else if (originalChestplates.containsKey(uuid) || fallImmunityPlayers.contains(uuid)) {
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
        fireworkUsed.clear();
        elytraBroken.clear();
        timeoutTasks.values().forEach(BukkitTask::cancel);
        timeoutTasks.clear();
        moonSmashUsed.clear();
        fallImmunityPlayers.clear();
    }
}
