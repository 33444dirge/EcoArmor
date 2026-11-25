package com.willfp.ecoarmor.sets

import com.willfp.eco.util.NumberUtils
import com.willfp.ecoarmor.plugin
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.persistence.PersistentDataType

object EffectiveDurabilityListener : Listener {
    @EventHandler
    fun listener(event: PlayerItemDamageEvent) {
        val itemStack = event.item
        val container = itemStack.persistentDataContainer
        val effectiveDurability =
            container.get(plugin.effectiveDurabilityKey, PersistentDataType.INTEGER)
                ?: return
        val maxDurability = itemStack.type.maxDurability.toInt()
        if (maxDurability <= 0) return
        val ratio = effectiveDurability.toDouble() / maxDurability
        val chance = 1 / ratio
        if (NumberUtils.randFloat(0.0, 1.0) > chance) {
            event.isCancelled = true
        }
    }
}