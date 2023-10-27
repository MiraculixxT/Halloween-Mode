package de.miraculixx.halloweench.event

import com.destroystokyo.paper.ParticleBuilder
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent
import de.miraculixx.kpaper.event.listen
import de.miraculixx.kpaper.event.register
import de.miraculixx.kpaper.event.unregister
import de.miraculixx.kpaper.extensions.bukkit.dispatchCommand
import de.miraculixx.kpaper.extensions.console
import de.miraculixx.kpaper.extensions.onlinePlayers
import de.miraculixx.kpaper.extensions.worlds
import de.miraculixx.kpaper.items.customModel
import de.miraculixx.kpaper.runnables.task
import de.miraculixx.kpaper.runnables.taskRunLater
import dev.jorel.commandapi.kotlindsl.commandTree
import dev.jorel.commandapi.kotlindsl.literalArgument
import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.FireworkExplodeEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.MerchantRecipe
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.io.File
import kotlin.random.Random
import kotlin.random.nextInt

class HalloweenChallenge {
    private val villagerKey = NamespacedKey("de.miraculixx.api", "challenge.halloween.villager")
    private val specialItems = HalloweenItems()
    private val villageTrades = listOf(
        craftRecipe(ItemStack(Material.CROSSBOW), 4, specialItems.candy().add(22)),
        craftRecipe(ItemStack(Material.ARROW).add(10), 4, specialItems.candy().add(5)),
        craftRecipe(specialItems.silverArrow().add(3), 2, specialItems.candy().add(32)),
        craftRecipe(specialItems.silverSword(), 1, specialItems.candy().add(61)),
        craftRecipe(specialItems.pflock(), 1, specialItems.candy().add(9)),
        craftRecipe(ItemStack(Material.BONE).add(2), 4, specialItems.candy().add(5)),
        craftRecipe(specialItems.nightVisionGoggles(), 1, specialItems.candy().add(11)),
        craftRecipe(ItemStack(Material.BOOK).add(4), 4, specialItems.candy().add(5)),
        craftRecipe(ItemStack(Material.APPLE).add(6), 4, specialItems.candy().add(10)),
        craftRecipe(ItemStack(Material.SPIDER_EYE), 4, specialItems.candy().add(4)),
        craftRecipe(ItemStack(Material.CAKE), 2, specialItems.candy().add(13)),
        craftRecipe(ItemStack(Material.GOLDEN_APPLE), 2, specialItems.candy().add(57), specialItems.candy().add(53)),
        craftRecipe(ItemStack(Material.STRING).add(8), 4, specialItems.candy().add(8)),
        craftRecipe(ItemStack(Material.LANTERN), 4, specialItems.candy().add(3)),
        craftRecipe(ItemStack(Material.CANDLE).add(2), 4, specialItems.candy().add(17)),
        craftRecipe(specialItems.rocket(), 1, specialItems.candy().add(16)),
        craftRecipe(specialItems.candy().add(12), 1, ItemStack(Material.PAPER).add(1), ItemStack(Material.HONEY_BOTTLE)),
    )
    private var spookyWorld: World? = null

    fun register() {
        onMove.register()
        onEquip.register()
        onInteract.register()
        onEntityInteract.register()
        onFireExplode.register()
        onKill.register()
        onSpawn.register()
    }

    fun unregister() {
        onMove.unregister()
        onEquip.unregister()
        onInteract.unregister()
        onEntityInteract.unregister()
        onFireExplode.unregister()
        onKill.unregister()
        onSpawn.unregister()
    }

    fun start(): Boolean {
        val world = worlds[0]
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true)
        startScheduler()

        world.setGameRule(GameRule.SEND_COMMAND_FEEDBACK, false)

//        spookyWorld = WorldCreator("halloween")
//            .biomeProvider(HalloweenBiomeProvider())
//            .environment(World.Environment.NORMAL)
//            .createWorld() ?: return false
//        val spawn = spookyWorld!!.spawnLocation
//        onlinePlayers.forEach { p -> p.teleportAsync(spawn) }
        return true
    }

    fun stop() {
        if (spookyWorld != null) {
            val spawn = worlds[0].spawnLocation
            spookyWorld?.players?.forEach { p -> p.teleport(spawn) }
            Bukkit.unloadWorld(spookyWorld!!, false)
            File(spookyWorld!!.name).deleteRecursively()
        }
    }

    private val onFireExplode = listen<FireworkExplodeEvent>(register = false) {
        val pos = it.entity.location
        taskRunLater(1) {
            repeat((10..15).random()) {
                pos.world.spawn(pos, Bat::class.java).apply {
                    equipment.helmet = specialItems.candy().add(if (Random.nextBoolean()) 2 else 1) // 1 or 2
                    equipment.helmetDropChance = 1f
                    health = 1.0
                }
            }
            pos.world.dropItemNaturally(pos, specialItems.candy()).apply {
                velocity.y = 0.5
            }
            pos.getNearbyPlayers(20.0).forEach { p -> p.playSound(p, Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1f, 1.3f) }
        }
    }

    private val onInteract = listen<PlayerInteractEvent>(register = false) {
        val block = it.clickedBlock ?: return@listen
        val position = it.interactionPoint ?: return@listen
        val type = block.type
        val player = it.player
        val item = it.item

        when {
            Tag.BEDS.isTagged(type) -> {
                it.isCancelled = true
                player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 15, 1, false, false, false))
                val world = player.world
                val targets = position.getNearbyPlayers(10.0)
                repeat(5) {
                    spawnBat(position, targets)
                }
                block.breakNaturally()
                return@listen
            }
        }
    }

    private val onEntityInteract = listen<PlayerInteractAtEntityEvent>(register = false) {
        val entity = it.rightClicked

        if (entity.type == EntityType.VILLAGER) {
            // Own trading logic
            val village = entity as Villager

            if (!village.persistentDataContainer.has(villagerKey)) {
                village.persistentDataContainer.set(villagerKey, PersistentDataType.BOOLEAN, true)
                village.equipment.helmet = ItemStack(Material.JACK_O_LANTERN)
                village.profession = Villager.Profession.CLERIC
                village.villagerExperience = 10
                val dummyList = villageTrades.toMutableList()
                village.recipes = buildList {
                    repeat((3..5).random()) {
                        val random = dummyList.random()
                        dummyList.remove(random)
                        add(random)
                    }
                }
            }
        }
    }

    private val onEquip = listen<PlayerArmorChangeEvent>(register = false) {
        val new = it.newItem.itemMeta?.customModel ?: 0
        val old = it.oldItem.itemMeta?.customModel ?: 0
        val player = it.player

        val nightVisionGogglesID = specialItems.nightVisionGogglesID
        when {
            old == nightVisionGogglesID && new != nightVisionGogglesID -> {
                ParticleBuilder(Particle.SPELL_MOB).offset(0.996078431372549, 0.9921568627450981, 0.0)
                    .count(0).extra(1.0).receivers(player).location(player.location).spawn()
            }

            old != nightVisionGogglesID && new == nightVisionGogglesID -> {
                ParticleBuilder(Particle.SPELL_MOB).offset(0.996078431372549, 0.9921568627450981, 0.03529411764705882)
                    .count(0).extra(1.0).receivers(player).location(player.location).spawn()
            }
        }
    }

    private val onMove = listen<PlayerMoveEvent>(register = false) {
        val from = it.from
        val to = it.to
        val fromBiome = from.block.biome
        val toBiome = to.block.biome
        val player = it.player

        val fromForest = fromBiome.name.endsWith("forest", true)
        val toForest = toBiome.name.endsWith("forest", true)
        when {
            fromForest && !toForest -> {
                player.removePotionEffect(PotionEffectType.DARKNESS)
            }

            !fromForest && toForest -> {
                player.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, -1, 1, false, false, false))
            }
        }
    }

    private val onSpawn = listen<CreatureSpawnEvent>(register = false) {
        if (it.spawnReason != CreatureSpawnEvent.SpawnReason.NATURAL) return@listen
        val type = it.entityType
        val loc = it.location
        when (val entity = it.entity) {
            is Cat -> entity.catType = Cat.Type.BLACK
            is Chicken -> {
                loc.world.spawn(loc, Slime::class.java, CreatureSpawnEvent.SpawnReason.SPELL).apply {
                    setWander(true)
                    equipment.helmet = ItemStack(if (Random.nextBoolean()) Material.BROWN_MUSHROOM else Material.RED_MUSHROOM)
                }
                it.isCancelled = true
            }
        }
    }

    private val onKill = listen<EntityDeathEvent>(register = false) {
        val entity = it.entity
        val loc = entity.location

        when (entity) {
            is Skeleton -> {
                it.drops.clear()
                repeat(3) {
                    val item = loc.world.dropItem(loc, ItemStack(Material.BONE))
                    if (Random.nextInt(1..3) == 1) {
                        if (Random.nextInt(1..3) == 1) {
                            item.pickupDelay = Int.MAX_VALUE
                            item.itemStack.editMeta { m -> m.customModel = 1 }
                        }
                        taskRunLater(20 * 6) {
                            if (item.isDead) return@taskRunLater
                            loc.getNearbyPlayers(10.0).forEach { p -> p.playSound(p, Sound.ENTITY_SKELETON_CONVERTED_TO_STRAY, 1f, 1f) }
                            ParticleBuilder(Particle.SMALL_FLAME).offset(0.1, 0.1, 0.1).extra(0.01).count(5).location(loc).allPlayers().spawn()
                            taskRunLater(20) spawn@{
                                if (item.isDead) return@spawn
                                loc.world.spawn(item.location, Skeleton::class.java)
                                loc.getNearbyPlayers(10.0).forEach { p -> p.playSound(p, Sound.ENTITY_SKELETON_HURT, 1f, 1f) }
                                item.remove()
                            }
                        }
                    }
                }
            }

            is Zombie -> {
                if (!entity.isAdult) return@listen
                val p = loc.getNearbyPlayers(15.0).firstOrNull()
                repeat((1..3).random()) { _ ->
                    val z = loc.world.spawn(loc, Zombie::class.java)
                    z.setBaby()
                    z.target = p
                }
            }

            is Witch -> {
                val targets = loc.getNearbyPlayers(10.0)
                val pos = loc.add(0.0, 1.0, 0.0)
                repeat(4) {
                    spawnBat(pos, targets, true)
                }
            }
        }
    }

    private val command = commandTree("halloween") {
        literalArgument("items") {
            specialItems.addItemsToCommand(this)
        }
    }

    private fun craftRecipe(result: ItemStack, maxUses: Int, ingredient: ItemStack, ingredient2: ItemStack? = null): MerchantRecipe {
        val recipe = MerchantRecipe(result, maxUses)
        recipe.addIngredient(ingredient)
        ingredient2?.let { recipe.addIngredient(it) }
        return recipe
    }

    private fun spawnBat(location: Location, targets: Collection<Player>, drop: Boolean = false) {
        val bat = location.world.spawn(location, Bat::class.java)
        targets.forEach { p -> p.playSound(location, Sound.ENTITY_BAT_AMBIENT, 1f, Random.nextFloat() + 0.5f) }
        console.dispatchCommand("particle dust_color_transition 0.000 0.000 0.000 1 0.388 0.000 0.325 ${location.x} ${location.y} ${location.z} .5 .1 .5 1 8 normal")
        if (drop) {
            bat.equipment.helmet = specialItems.candy()
            bat.equipment.helmetDropChance = 0.9f
        }
    }

    private fun startScheduler() {
        val mainWorld = worlds[0]
        var ambientTimer = 0

        task(true, period = 20) {
            val dayTime = mainWorld.time
            if (dayTime >= 23_000) mainWorld.time = 13_000L
            else if (dayTime < 13_000) mainWorld.time = 13_000

            onlinePlayers.forEach { p ->
                val pos = p.location
                val biome = pos.block.biome
                val inForest = biome.name.endsWith("forest", true)
                if (inForest) {
                    if (ambientTimer <= 0) {
                        val sound = when ((0..100).random()) {
                            in 0..40 -> Sound.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS
                            in 40..50 -> Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD
                            in 60..80 -> Sound.AMBIENT_WARPED_FOREST_MOOD
                            in 90..100 -> Sound.AMBIENT_WARPED_FOREST_ADDITIONS
                            else -> Sound.AMBIENT_BASALT_DELTAS_ADDITIONS
                        }
                        p.playSound(p, sound, 1f, 1f)
                    }
                }
            }

            if (ambientTimer <= 0) ambientTimer = (8..19).random()
            ambientTimer--
        }
    }
}