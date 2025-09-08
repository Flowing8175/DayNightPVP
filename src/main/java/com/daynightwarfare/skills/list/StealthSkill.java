package com.daynightwarfare.skills.list;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import com.comphenix.protocol.wrappers.Pair;
import com.daynightwarfare.TeamType;
import com.daynightwarfare.skills.Skill;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StealthSkill extends Skill {

    private ProtocolManager protocolManager;

    public StealthSkill() {
        super(
                "stealth",
                "<green>Stealth</green>",
                Collections.singletonList("<gray>Hold sneak for 3 seconds to gain a burst of speed and glowing.</gray>"),
                TeamType.APOSTLE_OF_LIGHT,
                null, // No item associated with this skill
                18L // Cooldown in seconds
        );
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    @Override
    public boolean execute(Player player) {
        // Apply Invisibility II for 3.5 seconds (70 ticks)
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 70, 1, true, false, true));
        // Apply Speed V for 3.5 seconds (70 ticks)
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 70, 4, true, false, true));
        // Apply Glowing for 3.5 seconds (70 ticks)
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 70, 0, true, false, true));

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_LAND, 1.0f, 0.75f);

        hidePlayerEquipment(player);

        return true;
    }

    private void hidePlayerEquipment(Player player) {
        PacketAdapter packetAdapter = new PacketAdapter(plugin, PacketType.Play.Server.ENTITY_EQUIPMENT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPacket().getIntegers().read(0) == player.getEntityId()) {
                    event.setCancelled(true);
                }
            }
        };
        protocolManager.addPacketListener(packetAdapter);

        // Hide equipment for all online players
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!onlinePlayer.equals(player)) {
                hideArmorForPlayer(player, onlinePlayer);
            }
        }

        // After 3.5 seconds, unregister listener and show equipment again
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            protocolManager.removePacketListener(packetAdapter);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_LAND, 1.0f, 0.65f);
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (!onlinePlayer.equals(player)) {
                    showArmorForPlayer(player, onlinePlayer);
                }
            }
        }, 70L);
    }

    private void hideArmorForPlayer(Player target, Player observer) {
        List<Pair<EnumWrappers.ItemSlot, ItemStack>> equipment = new ArrayList<>();
        for (EnumWrappers.ItemSlot slot : EnumWrappers.ItemSlot.values()) {
            equipment.add(new Pair<>(slot, null));
        }

        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
        packet.getIntegers().write(0, target.getEntityId());
        packet.getSlotStackPairLists().write(0, equipment);
        try {
            protocolManager.sendServerPacket(observer, packet);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send equipment packet: " + e.getMessage());
        }
    }

    private void showArmorForPlayer(Player target, Player observer) {
        List<Pair<EnumWrappers.ItemSlot, ItemStack>> equipment = new ArrayList<>();
        equipment.add(new Pair<>(EnumWrappers.ItemSlot.HEAD, target.getInventory().getHelmet()));
        equipment.add(new Pair<>(EnumWrappers.ItemSlot.CHEST, target.getInventory().getChestplate()));
        equipment.add(new Pair<>(EnumWrappers.ItemSlot.LEGS, target.getInventory().getLeggings()));
        equipment.add(new Pair<>(EnumWrappers.ItemSlot.FEET, target.getInventory().getBoots()));
        equipment.add(new Pair<>(EnumWrappers.ItemSlot.MAINHAND, target.getInventory().getItemInMainHand()));
        equipment.add(new Pair<>(EnumWrappers.ItemSlot.OFFHAND, target.getInventory().getItemInOffHand()));

        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
        packet.getIntegers().write(0, target.getEntityId());
        packet.getSlotStackPairLists().write(0, equipment);
        try {
            protocolManager.sendServerPacket(observer, packet);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send equipment packet: " + e.getMessage());
        }
    }
}
