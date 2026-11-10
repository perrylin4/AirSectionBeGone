package com.perry.asbg;

import com.perry.asbg.section.AirVoidSectionManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AirSectionBeGone implements ModInitializer {
    public static final String MOD_ID = "airsectionbegone";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> AirVoidSectionManager.clearCaches());
        LOGGER.info("AirSectionBeGone initialized for dedicated-server chunk-section deduplication");
    }
}
