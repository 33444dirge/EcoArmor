package com.willfp.ecoarmor

import com.willfp.eco.core.command.impl.PluginCommand
import com.willfp.eco.core.display.DisplayModule
import com.willfp.eco.core.items.CustomItem
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.TestableItem
import com.willfp.ecoarmor.commands.CommandEcoArmor
import com.willfp.ecoarmor.display.ArmorDisplay
import com.willfp.ecoarmor.libreforge.ConditionIsWearingSet
import com.willfp.ecoarmor.sets.ArmorSetEquipSoundListeners
import com.willfp.ecoarmor.sets.ArmorSets
import com.willfp.ecoarmor.sets.ArmorUtils
import com.willfp.ecoarmor.sets.EffectiveDurabilityListener
import com.willfp.ecoarmor.sets.PlayerArmorSetEventListeners
import com.willfp.ecoarmor.sets.PreventSkullPlaceListener
import com.willfp.ecoarmor.upgrades.AdvancementShardListener
import com.willfp.ecoarmor.upgrades.CrystalListener
import com.willfp.ecoarmor.upgrades.TierArgParser
import com.willfp.ecoarmor.upgrades.Tiers
import com.willfp.ecoarmor.util.DiscoverRecipeListener
import com.willfp.ecoarmor.libreforge.FilterArmorSet
import com.willfp.ecoarmor.libreforge.FilterArmorTier
import com.willfp.ecoarmor.libreforge.TriggerAdvanceArmor
import com.willfp.ecoarmor.libreforge.TriggerUpgradeArmorTier
import com.willfp.libreforge.conditions.Conditions
import com.willfp.libreforge.filters.Filters
import com.willfp.libreforge.loader.LibreforgePlugin
import com.willfp.libreforge.triggers.Triggers
import com.willfp.libreforge.loader.configs.ConfigCategory
import com.willfp.libreforge.registerSpecificHolderProvider
import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Listener
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

internal lateinit var plugin: EcoArmorPlugin
    private set

class EcoArmorPlugin : LibreforgePlugin() {
    // Immutable markers belong to this plugin instance; item values are still read live.
    internal val effectiveDurabilityKey = namespacedKeyFactory.create("effective-durability")
    internal val setKey = namespacedKeyFactory.create("set")
    internal val advancedKey = namespacedKeyFactory.create("advanced")
    internal val tierKey = namespacedKeyFactory.create("tier")
    internal val upgradeCrystalKey = namespacedKeyFactory.create("upgrade_crystal")
    internal val advancementShardKey = namespacedKeyFactory.create("advancement-shard")
    private val configuredItems = mutableMapOf<NamespacedKey, CustomItem>()

    // eco only exposes unconditional deletion in its public item API.
    @Suppress("UNCHECKED_CAST")
    private val ecoItemRegistry by lazy {
        val registry = try {
            val field = Items::class.java.getDeclaredField("REGISTRY")
            if (Modifier.isStatic(field.modifiers) && field.trySetAccessible()) {
                field.get(null) as? ConcurrentHashMap<NamespacedKey, TestableItem>
            } else {
                null
            }
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: SecurityException) {
            null
        }
        if (registry == null) {
            logger.warning("EcoArmor cannot verify eco item registry ownership. Retired registrations will be left in eco.")
        }
        registry
    }

    init {
        plugin = this
        Items.registerArgParser(TierArgParser())
    }

    override fun handleLoad() {
        Conditions.register(ConditionIsWearingSet)
        Triggers.register(TriggerAdvanceArmor)
        Triggers.register(TriggerUpgradeArmorTier)
        Filters.register(FilterArmorSet)
        Filters.register(FilterArmorTier)
    }

    override fun handleEnable() {
        registerSpecificHolderProvider<LivingEntity> {
            ArmorUtils.getActiveHolders(it)
        }
        // Recipes are registered on a 1-tick delay, so rebuild cache after they exist.
        scheduler.runLater(2) {
            DiscoverRecipeListener.reloadRecipeCache()
        }
    }

    override fun handleReload() {
        removeRetiredItems(ArmorSets.itemKeys + Tiers.itemKeys)
        ArmorUtils.clearCache()
        scheduler.runLater(2) {
            DiscoverRecipeListener.reloadRecipeCache()
        }
    }

    override fun handleDisable() {
        removeRetiredItems(emptySet())
    }

    internal fun registerConfiguredItem(item: CustomItem) {
        item.register()
        configuredItems[item.key] = item
    }

    private fun removeRetiredItems(activeKeys: Set<NamespacedKey>) {
        val retiredKeys = configuredItems.keys - activeKeys
        if (retiredKeys.isEmpty()) return

        val registry = ecoItemRegistry
        for (key in retiredKeys) {
            val item = configuredItems.getValue(key)
            // Aliases can reference the old object even after its original key changes hands.
            registry?.computeIfPresent(key) { _, current ->
                if (current === item) null else current
            }
            configuredItems.remove(key)
        }
    }

    override fun loadConfigCategories(): List<ConfigCategory> {
        return listOf(
            Tiers,
            ArmorSets
        )
    }

    override fun loadPluginCommands(): List<PluginCommand> {
        return listOf(
            CommandEcoArmor
        )
    }

    override fun loadListeners(): List<Listener> {
        return listOf(
            CrystalListener,
            AdvancementShardListener,
            EffectiveDurabilityListener,
            DiscoverRecipeListener,
            PreventSkullPlaceListener,
            ArmorSetEquipSoundListeners,
            PlayerArmorSetEventListeners
        )
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun createDisplayModule(): DisplayModule {
        return ArmorDisplay
    }
}
