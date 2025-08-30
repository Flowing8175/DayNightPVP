package com.daynightwarfare.listeners;

import com.daynightwarfare.DayNightPlugin;
import com.daynightwarfare.GameManager;
import com.daynightwarfare.TeamType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class SkillListener implements Listener {

    private final DayNightPlugin plugin;
    private final GameManager gameManager;
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> sneakStartTimes = new HashMap<>();
    private final Map<UUID, ItemStack> originalChestplates = new HashMap<>();
    private final Set<UUID> skillDisabledPlayers = new HashSet<>();
    private final NamespacedKey skillIdKey;
    private final NamespacedKey moonSmashKey;
    private final NamespacedKey sunsSpearKey;


    public SkillListener(DayNightPlugin plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
        this.skillIdKey = new NamespacedKey(plugin, "skill_id");
        this.moonSmashKey = new NamespacedKey(plugin, "moon_smash_active");
        this.sunsSpearKey = new NamespacedKey(plugin, "suns_spear_id");
    }

    private boolean isSkillItem(ItemStack item, String expectedSkillId) {
        if (item == null || !item.hasItemMeta()) return false;
        String skillId = item.getItemMeta().getPersistentDataContainer().get(skillIdKey, PersistentDataType.STRING);
        return expectedSkillId.equals(skillId);
    }

    private boolean checkCooldown(Player player, String skillId) {
        String key = player.getUniqueId().toString() + ":" + skillId;
        long now = System.currentTimeMillis();
        long cooldownMillis = plugin.getConfig().getLong("cooldowns." + skillId, 30) * 1000L;

        if (cooldowns.containsKey(key)) {
            long expires = cooldowns.get(key);
            if (now < expires) {
                long timeLeft = (expires - now) / 1000;
                player.sendMessage(Component.text("해당 스킬은 " + timeLeft + "초 후에 사용할 수 있습니다.", NamedTextColor.RED));
                return false;
            }
        }
        return true;
    }

    private void setCooldown(Player player, String skillId) {
        String key = player.getUniqueId().toString() + ":" + skillId;
        long cooldownMillis = plugin.getConfig().getLong("cooldowns." + skillId, 30) * 1000L;
        cooldowns.put(key, System.currentTimeMillis() + cooldownMillis);
    }

    private boolean canUseSkill(Player player, TeamType requiredTeam) {
        if (!gameManager.isGameInProgress() || gameManager.isGracePeriodActive()) {
            player.sendMessage(Component.text("지금은 스킬을 사용할 수 없습니다.", NamedTextColor.RED));
            return false;
        }
        if (gameManager.getPlayerTeam(player) != requiredTeam) {
            player.sendMessage(Component.text("이 스킬은 " + requiredTeam.getStyledDisplayName() + " 팀만 사용할 수 있습니다.", NamedTextColor.RED));
            return false;
        }
        if (skillDisabledPlayers.contains(player.getUniqueId())) {
            player.sendMessage(Component.text("스킬이 비활성화되었습니다.", NamedTextColor.RED));
            return false;
        }
        return true;
    }

    // APOSTLE OF THE SUN SKILLS
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (isSkillItem(item, "solar-flare")) {
            handleSolarFlare(player);
        } else if (isSkillItem(item, "suns-spear")) {
            handleSunsSpear(player);
        } else if (isSkillItem(item, "afterglow")) {
            handleAfterglow(player);
        } else if (isSkillItem(item, "mirror-dash")) {
            handleMirrorDash(player);
        } else if (isSkillItem(item, "moons-chain")) {
            handleMoonsChain(player);
        }
    }

    private void handleSolarFlare(Player player) {
        if (!canUseSkill(player, TeamType.APOSTLE_OF_LIGHT) || !checkCooldown(player, "solar-flare")) return;

        for (Entity entity : player.getNearbyEntities(15, 15, 15)) {
            if (!(entity instanceof LivingEntity target)) continue;

            TeamType targetTeam = gameManager.getPlayerTeam((Player) target);
            if (targetTeam != null && targetTeam != TeamType.APOSTLE_OF_LIGHT) { // Enemy
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 1));
            } else if (targetTeam == TeamType.APOSTLE_OF_LIGHT) { // Ally
                target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 0));
                // Attack speed boost would require attribute modifiers, which is more complex. Sticking to potion effects.
            }
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1f);
        setCooldown(player, "solar-flare");
    }

    private void handleSunsSpear(Player player) {
        if (!canUseSkill(player, TeamType.APOSTLE_OF_LIGHT) || !checkCooldown(player, "suns-spear")) return;

        Trident spear = player.launchProjectile(Trident.class, player.getEyeLocation().getDirection());
        spear.setGlowing(true);
        spear.getPersistentDataContainer().set(sunsSpearKey, PersistentDataType.STRING, player.getUniqueId().toString());

        setCooldown(player, "suns-spear");
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident spear)) return;
        if (!spear.getPersistentDataContainer().has(sunsSpearKey, PersistentDataType.STRING)) return;

        Player shooter = Bukkit.getPlayer(UUID.fromString(spear.getPersistentDataContainer().get(sunsSpearKey, PersistentDataType.STRING)));
        if (shooter == null) return;

        if (event.getHitEntity() instanceof LivingEntity target) {
            target.damage(6, shooter);
            target.setFireTicks(40);
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
        } else if (event.getHitBlock() != null) {
            Block hitBlock = event.getHitBlock();
            Block lightBlock = hitBlock.getRelative(event.getHitBlockFace());
            if (lightBlock.getType() == Material.AIR) {
                lightBlock.setType(Material.LIGHT);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (lightBlock.getType() == Material.LIGHT) {
                            lightBlock.setType(Material.AIR);
                        }
                        spear.remove();
                    }
                }.runTaskLater(plugin, 60L); // 3 seconds
            }
        }
         if (spear.isValid()) spear.remove();
    }

    private void handleAfterglow(Player player) {
        if (!canUseSkill(player, TeamType.APOSTLE_OF_LIGHT) || !checkCooldown(player, "afterglow")) return;

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 10) { // 5 seconds (100 ticks / 10 ticks per run)
                    this.cancel();
                    return;
                }
                player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.01);
                for (Entity entity : player.getNearbyEntities(8, 8, 8)) {
                    if (entity instanceof LivingEntity target && entity != player) {
                         if (gameManager.getPlayerTeam((Player) target) != TeamType.APOSTLE_OF_LIGHT) {
                            target.damage(1, player);
                         }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 10L);

        setCooldown(player, "afterglow");
    }

    private void handleMirrorDash(Player player) {
        if (!canUseSkill(player, TeamType.APOSTLE_OF_LIGHT) || !checkCooldown(player, "mirror-dash")) return;

        Vector playerDirection = player.getEyeLocation().getDirection().normalize();
        LivingEntity bestTarget = null;
        double bestAngle = 45.0;

        for (Entity entity : player.getNearbyEntities(30, 30, 30)) {
            if (entity instanceof LivingEntity target && entity != player && gameManager.getPlayerTeam((Player)target) != TeamType.APOSTLE_OF_LIGHT) {
                Vector targetDirection = target.getEyeLocation().subtract(player.getEyeLocation()).toVector().normalize();
                double angle = Math.toDegrees(Math.acos(playerDirection.dot(targetDirection)));
                if (angle < bestAngle) {
                    bestAngle = angle;
                    bestTarget = target;
                }
            }
        }

        if (bestTarget != null) {
            Vector targetLookDir = bestTarget.getLocation().getDirection().normalize();
            Location behind = bestTarget.getLocation().subtract(targetLookDir.multiply(3));

            // Basic safety check
            if (behind.getBlock().isPassable() && behind.clone().add(0, 1, 0).getBlock().isPassable()) {
                player.teleport(behind);
                setCooldown(player, "mirror-dash");
            } else {
                 player.sendMessage(Component.text("대상을 추적할 수 없습니다.", NamedTextColor.RED));
            }
        } else {
            player.sendMessage(Component.text("시야에 대상이 없습니다.", NamedTextColor.RED));
        }
    }

    // APOSTLE OF THE MOON SKILLS

    private void handleMoonsChain(Player player) {
        if (!canUseSkill(player, TeamType.APOSTLE_OF_MOON) || !checkCooldown(player, "moons-chain")) return;

        for (Entity entity : player.getNearbyEntities(8, 8, 8)) {
            if (entity instanceof Player target && entity != player && gameManager.getPlayerTeam(target) != TeamType.APOSTLE_OF_MOON) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 2));
                skillDisabledPlayers.add(target.getUniqueId());
                Bukkit.getScheduler().runTaskLater(plugin, () -> skillDisabledPlayers.remove(target.getUniqueId()), 120L); // 6 seconds
            }
        }
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1f, 0.7f);
        setCooldown(player, "moons-chain");
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (event.isSneaking()) {
            sneakStartTimes.put(uuid, System.currentTimeMillis());
            // Moon Smash Logic
            if (player.isGliding() && canUseSkill(player, TeamType.APOSTLE_OF_MOON) && checkCooldown(player, "moon-smash")) {
                 player.getPersistentDataContainer().set(moonSmashKey, PersistentDataType.BYTE, (byte)1);
                 player.setVelocity(new Vector(0, -10, 0));
                 setCooldown(player, "moon-smash");
            }
        } else { // Not sneaking anymore
            Long startTime = sneakStartTimes.remove(uuid);
            if (startTime == null) return;

            // Shadow Wings Logic
            if ((System.currentTimeMillis() - startTime) >= 2000) {
                if (canUseSkill(player, TeamType.APOSTLE_OF_MOON) && checkCooldown(player, "shadow-wings")) {
                    ItemStack chestplate = player.getInventory().getChestplate();
                    if (chestplate != null) originalChestplates.put(uuid, chestplate.clone());

                    ItemStack elytra = new ItemStack(Material.ELYTRA);
                    elytra.addUnsafeEnchantment(Enchantment.DURABILITY, 10);
                    elytra.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
                    ItemMeta meta = elytra.getItemMeta();
                    if (meta instanceof Damageable) {
                         ((Damageable) meta).setDamage(elytra.getType().getMaxDurability() - 5);
                    }
                    elytra.setItemMeta(meta);

                    player.getInventory().setChestplate(elytra);
                    player.getInventory().addItem(new ItemStack(Material.FIREWORK_ROCKET));
                    setCooldown(player, "shadow-wings");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (originalChestplates.containsKey(player.getUniqueId()) && !player.isGliding() && player.isOnGround()) {
            player.getInventory().setChestplate(originalChestplates.remove(player.getUniqueId()));
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Restore chestplate on fall damage
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && originalChestplates.containsKey(player.getUniqueId())) {
             player.getInventory().setChestplate(originalChestplates.remove(player.getUniqueId()));
        }

        // Moon Smash damage
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (player.getPersistentDataContainer().has(moonSmashKey, PersistentDataType.BYTE)) {
                event.setCancelled(true);
                player.getPersistentDataContainer().remove(moonSmashKey);

                Location loc = player.getLocation();
                player.getWorld().playSound(loc, Sound.BLOCK_ANVIL_LAND, 1.5f, 0.5f);
                player.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, loc, 5);

                for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
                    if (entity instanceof LivingEntity target && gameManager.getPlayerTeam((Player)target) != TeamType.APOSTLE_OF_MOON) {
                        target.damage(event.getDamage() * 0.5, player); // Half of original fall damage
                        Vector knockback = target.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(1.5);
                        target.setVelocity(knockback);
                    }
                }
            }
        }
    }
}
