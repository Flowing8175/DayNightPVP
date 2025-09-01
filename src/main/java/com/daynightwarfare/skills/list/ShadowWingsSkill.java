package com.daynightwarfare.skills.list;

import com.daynightwarfare.TeamType;
import com.daynightwarfare.skills.Skill;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShadowWingsSkill extends Skill {

    private final Map<UUID, ItemStack> originalChestplates = new HashMap<>();
    private final Map<UUID, Boolean> shadowWingsGlided = new HashMap<>();

    public ShadowWingsSkill() {
        super(
                "shadow-wings",
                "<aqua>그림자 날개</aqua>",
                Arrays.asList("<gray>우클릭하여 일시적으로 비행합니다.</gray>"),
                TeamType.APOSTLE_OF_MOON,
                Material.FEATHER,
                60L
        );
    }

    @Override
    public void execute(Player player) {
        UUID uuid = player.getUniqueId();
        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate != null) {
            originalChestplates.put(uuid, chestplate.clone());
        }

        shadowWingsGlided.put(uuid, false);

        ItemStack elytra = new ItemStack(Material.ELYTRA);
        elytra.addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1);
        ItemMeta meta = elytra.getItemMeta();
        if (meta instanceof Damageable) {
            ((Damageable) meta).setDamage(elytra.getType().getMaxDurability() - 5);
        }
        elytra.setItemMeta(meta);

        player.getInventory().setChestplate(elytra);
        player.getInventory().addItem(new ItemStack(Material.FIREWORK_ROCKET));
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
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && originalChestplates.containsKey(player.getUniqueId())) {
            player.getInventory().setChestplate(originalChestplates.remove(player.getUniqueId()));
            shadowWingsGlided.remove(player.getUniqueId());
        }
    }
}
