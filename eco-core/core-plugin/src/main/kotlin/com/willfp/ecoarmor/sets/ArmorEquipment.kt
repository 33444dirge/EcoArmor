package com.willfp.ecoarmor.sets

import io.papermc.paper.ServerBuildInfo
import net.minecraft.world.entity.EquipmentSlot
import org.bukkit.craftbukkit.entity.CraftLivingEntity
import org.bukkit.craftbukkit.inventory.CraftEntityEquipment
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.EntityEquipment
import org.bukkit.inventory.ItemStack

internal object ArmorEquipment {
    @Volatile
    private var nativeSupported = hasVerifiedCore()

    fun read(entity: LivingEntity, equipment: EntityEquipment): List<ItemStack?> {
        if (entity !is Player && nativeSupported) {
            try {
                if (NativeArmor.isEmpty(entity, equipment)) return emptyList()
            } catch (_: LinkageError) {
                nativeSupported = false
            }
        }
        // Bukkit returns a fresh snapshot. The scan only reads its list view.
        return equipment.armorContents.asList()
    }

    private fun hasVerifiedCore(): Boolean {
        return try {
            val info = ServerBuildInfo.buildInfo()
            info.minecraftVersionId() == "26.2" &&
                info.gitCommit().orElse("").startsWith("f44c27e")
        } catch (_: LinkageError) {
            false
        }
    }

    // Keep CraftBukkit and Minecraft symbols behind the optional runtime boundary.
    private object NativeArmor {
        fun isEmpty(entity: LivingEntity, equipment: EntityEquipment): Boolean {
            if (equipment.javaClass != CraftEntityEquipment::class.java ||
                entity !is CraftLivingEntity ||
                entity.javaClass.classLoader !== CraftLivingEntity::class.java.classLoader
            ) return false

            // The target core's getHandle enforces region ownership.
            val handle = entity.handle
            return handle.getItemBySlot(EquipmentSlot.FEET).isEmpty &&
                handle.getItemBySlot(EquipmentSlot.LEGS).isEmpty &&
                handle.getItemBySlot(EquipmentSlot.CHEST).isEmpty &&
                handle.getItemBySlot(EquipmentSlot.HEAD).isEmpty
        }
    }
}
