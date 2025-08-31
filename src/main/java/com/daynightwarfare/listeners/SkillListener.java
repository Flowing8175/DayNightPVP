package com.daynightwarfare.listeners;

import com.daynightwarfare.DayNightPlugin;
import com.daynightwarfare.GameManager;
import com.daynightwarfare.TeamType;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
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
    private final Map<UUID, Boolean> shadowWingsGlided = new HashMap<>();
    private final Map<UUID, Long> friendlyFireWarningCooldowns = new HashMap<>();
    private final Set<UUID> skillDisabledPlayers = new HashSet<>();
    private final NamespacedKey skillIdKey;
    private final NamespacedKey moonSmashKey;
    private final NamespacedKey sunsSpearKey;
    private final MiniMessage miniMessage;


    public SkillListener(DayNightPlugin plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
        this.skillIdKey = new NamespacedKey(plugin, "skill_id");
        this.moonSmashKey = new NamespacedKey(plugin, "moon_smash_active");
        this.sunsSpearKey = new NamespacedKey(plugin, "suns_spear_id");
        this.miniMessage = MiniMessage.miniMessage();
    }

    private boolean isSkillItem(ItemStack item, String expectedSkillId) {
        if (item == null || !item.hasItemMeta()) return false;
        String skillId = item.getItemMeta().getPersistentDataContainer().get(skillIdKey, PersistentDataType.STRING);
        return expectedSkillId.equals(skillId);
    }

    private boolean isAnySkillItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(skillIdKey, PersistentDataType.STRING);
    }

    private boolean checkCooldown(Player player, String skillId) {
        String key = player.getUniqueId().toString() + ":" + skillId;
        long now = System.currentTimeMillis();
        long cooldownMillis = plugin.getConfig().getLong("cooldowns." + skillId, 30) * 1000L;

        if (cooldowns.containsKey(key)) {
            long expires = cooldowns.get(key);
            if (now < expires) {
                long timeLeft = (expires - now) / 1000;
                player.sendMessage(miniMessage.deserialize("<red>해당 스킬은 " + timeLeft + "초 후에 사용할 수 있습니다.</red>"));
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
            player.sendMessage(miniMessage.deserialize("<red>지금은 스킬을 사용할 수 없습니다.</red>"));
            return false;
        }
        if (gameManager.getPlayerTeam(player) != requiredTeam) {
            String teamColor = requiredTeam == TeamType.APOSTLE_OF_LIGHT ? "<yellow>" : "<aqua>";
            player.sendMessage(miniMessage.deserialize("<red>이 스킬은 " + teamColor + requiredTeam.getDisplayName() + "</color> 팀만 사용할 수 있습니다.</red>"));
            return false;
        }
        if (skillDisabledPlayers.contains(player.getUniqueId())) {
            player.sendMessage(miniMessage.deserialize("<red>스킬이 비활성화되었습니다.</red>"));
            return false;
        }
        return true;
    }

    //<editor-fold desc="Vanilla Behavior Cancellers">
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isAnySkillItem(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager) || !(event.getEntity() instanceof Player victim)) {
            return;
        }

        // Prevent skill sword melee
        ItemStack itemInHand = damager.getInventory().getItemInMainHand();
        if (isSkillItem(itemInHand, "suns-spear")) {
            event.setCancelled(true);
        }

        // Prevent friendly fire
        TeamType damagerTeam = gameManager.getPlayerTeam(damager);
        TeamType victimTeam = gameManager.getPlayerTeam(victim);

        if (damagerTeam != null && damagerTeam == victimTeam) {
            event.setCancelled(true);

            long now = System.currentTimeMillis();
            long cooldown = friendlyFireWarningCooldowns.getOrDefault(damager.getUniqueId(), 0L);

            if (now > cooldown) {
                damager.sendMessage(miniMessage.deserialize("<red>팀원을 공격할 수 없습니다!</red>"));
                friendlyFireWarningCooldowns.put(damager.getUniqueId(), now + 1000);
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (isAnySkillItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }
    //</editor-fold>


    //<editor-fold desc="Skill Event Handlers">
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

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
        } else if (isSkillItem(item, "shadow-wings")) {
            handleShadowWings(player);
        }
    }

    private void handleSolarFlare(Player player) {
        if (!canUseSkill(player, TeamType.APOSTLE_OF_LIGHT) || !checkCooldown(player, "solar-flare")) return;

        for (Player targetPlayer : Bukkit.getOnlinePlayers()) {
            if (targetPlayer.equals(player)) continue;
            if (targetPlayer.getLocation().distanceSquared(player.getLocation()) > 15 * 15) continue;

            TeamType targetTeam = gameManager.getPlayerTeam(targetPlayer);
            if (targetTeam != null && targetTeam != TeamType.APOSTLE_OF_LIGHT) { // Enemy
                targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0));
                targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 70, 1)); // 3.5 seconds
            } else if (targetTeam == TeamType.APOSTLE_OF_LIGHT) { // Ally
                targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 0));
            }
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1f);
        setCooldown(player, "solar-flare");
    }

    private void handleSunsSpear(Player player) {
        if (!canUseSkill(player, TeamType.APOSTLE_OF_LIGHT) || !checkCooldown(player, "suns-spear")) return;

        Vector velocity = player.getEyeLocation().getDirection().multiply(3);
        Trident spear = player.launchProjectile(Trident.class, velocity);
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

                for (Player targetPlayer : Bukkit.getOnlinePlayers()) {
                    if (targetPlayer.equals(player)) continue;
                    if (targetPlayer.getLocation().distanceSquared(player.getLocation()) > 6 * 6) continue;

                    if (gameManager.getPlayerTeam(targetPlayer) != TeamType.APOSTLE_OF_LIGHT) {
                        targetPlayer.damage(1.0); // Sourceless damage to prevent knockback
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 10L);

        setCooldown(player, "afterglow");
    }

    private void handleMirrorDash(Player player) {
        if (!canUseSkill(player, TeamType.APOSTLE_OF_LIGHT) || !checkCooldown(player, "mirror-dash")) return;

        Vector playerDirection = player.getEyeLocation().getDirection();
        playerDirection.setY(0).normalize(); // Ignore Y-axis for cone check

        Player bestTarget = null;
        double closestDot = -1.0; // Dot product ranges from -1 to 1. Closer to 1 is better.
        double coneThreshold = Math.cos(Math.toRadians(22.5)); // Pre-calculate cosine of half-angle

        for (Player targetPlayer : Bukkit.getOnlinePlayers()) {
            if (targetPlayer.equals(player) || targetPlayer.getWorld() != player.getWorld() || gameManager.getPlayerTeam(targetPlayer) == TeamType.APOSTLE_OF_LIGHT) {
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
                setCooldown(player, "mirror-dash");
            } else {
                 player.sendMessage(miniMessage.deserialize("<red>대상을 추적할 수 없습니다.</red>"));
            }
        } else {
            player.sendMessage(miniMessage.deserialize("<red>시야에 대상이 없습니다.</red>"));
        }
    }

    private void handleMoonsChain(Player player) {
        if (!canUseSkill(player, TeamType.APOSTLE_OF_MOON) || !checkCooldown(player, "moons-chain")) return;

        for (Player targetPlayer : Bukkit.getOnlinePlayers()) {
            if (targetPlayer.equals(player)) continue;
            if (targetPlayer.getLocation().distanceSquared(player.getLocation()) > 8 * 8) continue;

            if (gameManager.getPlayerTeam(targetPlayer) != TeamType.APOSTLE_OF_MOON) {
                targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 2));
                skillDisabledPlayers.add(targetPlayer.getUniqueId());
                Bukkit.getScheduler().runTaskLater(plugin, () -> skillDisabledPlayers.remove(targetPlayer.getUniqueId()), 120L); // 6 seconds
            }
        }
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1f, 0.7f);
        setCooldown(player, "moons-chain");
    }

    private void handleShadowWings(Player player) {
        if (!canUseSkill(player, TeamType.APOSTLE_OF_MOON) || !checkCooldown(player, "shadow-wings")) return;

        UUID uuid = player.getUniqueId();
        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate != null) originalChestplates.put(uuid, chestplate.clone());

        shadowWingsGlided.put(uuid, false);

        ItemStack elytra = new ItemStack(Material.ELYTRA);
        elytra.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
        ItemMeta meta = elytra.getItemMeta();
        if (meta instanceof Damageable) {
            ((Damageable) meta).setDamage(elytra.getType().getMaxDurability() - 6);
        }
        elytra.setItemMeta(meta);

        player.getInventory().setChestplate(elytra);
        player.getInventory().addItem(new ItemStack(Material.FIREWORK_ROCKET));
        setCooldown(player, "shadow-wings");
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (event.isSneaking()) {
            if (player.isGliding() && canUseSkill(player, TeamType.APOSTLE_OF_MOON) && checkCooldown(player, "moon-smash")) {
                 player.getPersistentDataContainer().set(moonSmashKey, PersistentDataType.BYTE, (byte)1);
                 player.setGliding(false);
                 player.setVelocity(new Vector(0, -2.5, 0));
                 setCooldown(player, "moon-smash");
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!originalChestplates.containsKey(uuid)) return;

        boolean hasGlided = shadowWingsGlided.getOrDefault(uuid, false);

        if (!hasGlided && player.isGliding()) {
            shadowWingsGlided.put(uuid, true);
        } else if (hasGlided && !player.isGliding() && player.isOnGround()) {
            player.getInventory().setChestplate(originalChestplates.remove(uuid));
            shadowWingsGlided.remove(uuid);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && originalChestplates.containsKey(player.getUniqueId())) {
             player.getInventory().setChestplate(originalChestplates.remove(player.getUniqueId()));
             shadowWingsGlided.remove(player.getUniqueId());
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (player.getPersistentDataContainer().has(moonSmashKey, PersistentDataType.BYTE)) {
                event.setCancelled(true);
                player.getPersistentDataContainer().remove(moonSmashKey);

                Location loc = player.getLocation();
                player.getWorld().playSound(loc, Sound.BLOCK_ANVIL_LAND, 1.5f, 0.5f);
                player.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, loc, 5);

                for (Player targetPlayer : Bukkit.getOnlinePlayers()) {
                    if (targetPlayer.equals(player)) continue;
                    if (targetPlayer.getLocation().distanceSquared(player.getLocation()) > 5*5) continue;

                    if (gameManager.getPlayerTeam(targetPlayer) != TeamType.APOSTLE_OF_MOON) {
                        targetPlayer.damage(event.getDamage() * 0.5, player);
                        Vector knockback = targetPlayer.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(1.5);
                        targetPlayer.setVelocity(knockback);
                    }
                }
            }
        }
    }
    //</editor-fold>
}
