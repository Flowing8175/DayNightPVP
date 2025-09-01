package com.daynightwarfare.skills.list;

import com.daynightwarfare.TeamType;
import com.daynightwarfare.skills.Skill;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShadowWingsSkill extends Skill {

    private final Map<UUID, ItemStack> originalChestplates = new HashMap<>();
    private final Map<UUID, Boolean> fireworkUsed = new HashMap<>();
    private final Map<UUID, Boolean> elytraBroken = new HashMap<>();
    private final NamespacedKey fireworkKey;

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
    }

    @Override
    public void execute(Player player) {
        UUID uuid = player.getUniqueId();
        // Clear any previous state just in case
        removeElytra(player);

        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate != null) {
            originalChestplates.put(uuid, chestplate.clone());
        }

        fireworkUsed.put(uuid, false);
        elytraBroken.put(uuid, false);

        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta meta = elytra.getItemMeta();
        if (meta instanceof Damageable) {
            ((Damageable) meta).setDamage(elytra.getType().getMaxDurability() - 1);
        }
        elytra.setItemMeta(meta);
        player.getInventory().setChestplate(elytra);

        ItemStack firework = new ItemStack(Material.FIREWORK_ROCKET);
        ItemMeta fireworkMeta = firework.getItemMeta();
        fireworkMeta.getPersistentDataContainer().set(fireworkKey, PersistentDataType.BYTE, (byte) 1);
        firework.setItemMeta(fireworkMeta);
        player.getInventory().addItem(firework);
    }

    private void removeElytra(Player player) {
        UUID uuid = player.getUniqueId();
        ItemStack originalChestplate = originalChestplates.remove(uuid);
        if (player.getInventory().getChestplate() != null && player.getInventory().getChestplate().getType() == Material.ELYTRA) {
            player.getInventory().setChestplate(originalChestplate);
        }
        fireworkUsed.remove(uuid);
        elytraBroken.remove(uuid);
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
        if (originalChestplates.containsKey(player.getUniqueId())) {
            if (event.getBrokenItem().getType() == Material.ELYTRA) {
                elytraBroken.put(player.getUniqueId(), true);
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        if (originalChestplates.containsKey(uuid)) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                event.setCancelled(true);
            }
        }
    }

    @Override
    public void cleanUp() {
        for (UUID uuid : originalChestplates.keySet()) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                removeElytra(player);
            }
        }
        originalChestplates.clear();
        fireworkUsed.clear();
        elytraBroken.clear();
    }
}
