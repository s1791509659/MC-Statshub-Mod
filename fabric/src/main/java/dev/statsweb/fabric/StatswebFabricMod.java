package dev.statsweb.fabric;

import dev.statsweb.core.Slf4jStatswebLogger;
import dev.statsweb.core.StatswebCore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric shell for the Stats Web core.
 *
 * <p>It touches no Minecraft class at all — the Fabric Loader API alone provides the game
 * directory, the config directory and the mod version. That property is what lets one jar
 * run on every Minecraft version whose loader is new enough, and it is also why this
 * module does not need the Loom toolchain.
 */
public class StatswebFabricMod implements ModInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(StatswebCore.MOD_ID);

    private StatswebCore core;

    @Override
    public void onInitialize() {
        String version = FabricLoader.getInstance().getModContainer(StatswebCore.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");

        core = new StatswebCore(version, new Slf4jStatswebLogger(LOG));
        core.start(FabricLoader.getInstance().getGameDir(), FabricLoader.getInstance().getConfigDir());

        // Shut the web server down cleanly when the game server stops.
        Runtime.getRuntime().addShutdownHook(new Thread(core::stop, "statsweb-shutdown"));
    }
}
