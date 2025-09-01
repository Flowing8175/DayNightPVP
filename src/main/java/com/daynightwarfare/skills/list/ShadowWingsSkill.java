package com.daynightwarfare.skills.list;

import com.daynightwarfare.TeamType;
import com.daynightwarfare.skills.Skill;
import org.bukkit.Location;
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
    private final Map<UUID, BukkitTask> timeoutTasks = new HashMap<>();
    private final Map<UUID, Long> moonSmashCooldowns = new HashMap<>();
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
        removeElytra(player); // Clear any previous state

        originalChestplates.put(uuid, player.getInventory().getChestplate());
        fireworkUsed.put(uuid, false);
        elytraBroken.put(uuid, false);

        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta meta = elytra.getItemMeta();
        meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
        if (meta instanceof Damageable) {
            ((Damageable) meta).setDamage(elytra.getType().getMaxDurability() - 3);
        }
        elytra.setItemMeta(meta);
        player.getInventory().setChestplate(elytra);

        ItemStack firework = new ItemStack(Material.FIREWORK_ROCKET);
        ItemMeta fireworkMeta = firework.getItemMeta();
        fireworkMeta.getPersistentDataContainer().set(fireworkKey, PersistentDataType.BYTE, (byte) 1);
        firework.setItemMeta(fireworkMeta);
        player.getInventory().addItem(firework);

        BukkitTask timeoutTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> removeElytra(player), 200L);
        timeoutTasks.put(uuid, timeoutTask);

        return true;
    }

    private void removeElytra(Player player) {
        UUID uuid = player.getUniqueId();
        if (!originalChestplates.containsKey(uuid)) return;

        BukkitTask timeoutTask = timeoutTasks.remove(uuid);
        if (timeoutTask != null) timeoutTask.cancel();

        player.getInventory().setChestplate(originalChestplates.remove(uuid));
        player.getInventory().remove(Material.FIREWORK_ROCKET); // Also removes the firework

        fireworkUsed.remove(uuid);
        elytraBroken.remove(uuid);

        // Grant lingering fall damage immunity
        fallImmunityPlayers.add(uuid);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> fallImmunityPlayers.remove(uuid), 60L); // 3 seconds
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
            removeElytra(player);
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
        if (!event.isSneaking() || !player.isGliding() || !originalChestplates.containsKey(player.getUniqueId())) {
            return;
        }

        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("cooldowns.moon-smash", 30) * 1000L;
        if (moonSmashCooldowns.getOrDefault(player.getUniqueId(), 0L) > now) {
            return; // Cooldown active
        }

        player.getPersistentDataContainer().set(moonSmashKey, PersistentDataType.BYTE, (byte) 1);
        player.setGliding(false);
        player.setVelocity(new Vector(0, -2.5, 0));
        moonSmashCooldowns.put(player.getUniqueId(), now + cooldown);
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
                player.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, player.getLocation(), 5);

                for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
                    if (entity instanceof Player && gameManager.getTeamManager().getPlayerTeam((Player) entity) != TeamType.APOSTLE_OF_MOON) {
                        ((LivingEntity) entity).damage(event.getDamage() * 0.5, player);
                        Vector knockback = entity.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.5);
                        entity.setVelocity(knockback);
                    }
                }
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
                removeElytra(player);
            }
        }
        originalChestplates.clear();
        fireworkUsed.clear();
        elytraBroken.clear();
        timeoutTasks.values().forEach(BukkitTask::cancel);
        timeoutTasks.clear();
        moonSmashCooldowns.clear();
        fallImmunityPlayers.clear();
    }
}
