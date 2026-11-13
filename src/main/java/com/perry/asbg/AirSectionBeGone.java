package com.perry.asbg;

import com.perry.asbg.section.ASBGSectionManager;
import com.perry.asbg.section.SectionRefoldScheduler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AirSectionBeGone implements ModInitializer {
    public static final String MOD_ID = "airsectionbegone";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ServerTickEvents.END_WORLD_TICK.register(SectionRefoldScheduler::tick);
        ServerWorldEvents.UNLOAD.register((server, level) -> SectionRefoldScheduler.clear(level));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ASBGSectionManager.clearCaches());
        LOGGER.info(
                "AirSectionBeGone initialized for dedicated-server uniform-container deduplication ({})",
                ASBGSectionManager.isEnabled() ? "enabled" : "disabled"
        );
    }
}
