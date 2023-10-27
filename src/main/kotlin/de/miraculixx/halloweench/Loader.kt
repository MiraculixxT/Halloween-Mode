package de.miraculixx.halloweench

import de.miraculixx.halloweench.event.HalloweenChallenge
import de.miraculixx.kpaper.main.KPaper
import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIBukkitConfig

class Loader : KPaper() {
    private var instance: HalloweenChallenge? = null

    override fun load() {
        CommandAPI.onLoad(CommandAPIBukkitConfig(this).silentLogs(true))
    }

    override fun startup() {
        CommandAPI.onEnable()

        instance = HalloweenChallenge()
        instance?.register()
        instance?.start()
    }

    override fun shutdown() {
        CommandAPI.onDisable()
    }
}