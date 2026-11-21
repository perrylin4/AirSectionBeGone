package com.perry.asbg.test;

import com.perry.asbg.poi.PoiColumnTracker;
import com.perry.asbg.section.ASBGSectionManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.Optional;
import net.caffeinemc.mods.lithium.common.world.interests.RegionBasedStorageSectionExtended;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SectionStorage;

/** Run with -PpoiIntegrationTest; its POI files and server live under build/. */
public final class PoiIntegrationTest implements ModInitializer {
    private static final BlockPos JOB = new BlockPos(16000, 200, 16000);
    private static final BlockPos PORTAL = JOB.above(32);
    private static final BlockPos REMOVED = JOB.above(64);

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                SectionIntegrationChecks.run(server);
                HeightmapIntegrationChecks.run(server);
                run(server);
                Files.writeString(Path.of("poi-test-result.txt"), "PASS " + System.getProperty("asbg.poiTest.stage"));
                System.out.println("ASBG_POI_TEST_PASS");
            } catch (Throwable failure) {
                failure.printStackTrace();
                try {
                    Files.writeString(Path.of("poi-test-result.txt"), "FAIL " + failure);
                } catch (Exception ignored) {
                }
                System.err.println("ASBG_POI_TEST_FAIL " + failure);
            } finally {
                server.halt(false);
            }
        });
    }

    private static void run(MinecraftServer server) throws Exception {
        try (PoiManager poi = new PoiManager(
                new RegionStorageInfo("asbg-test", Level.OVERWORLD, "poi"), Path.of("probe-poi"),
                DataFixers.getDataFixer(), false, server.registryAccess(), server,
                LevelHeightAccessor.create(-352, 1728)
        )) {
            Long2ObjectMap<?> data = field(poi, "storage");
            LongSet loaded = field(poi, "loadedChunks", SectionStorage.class);
            boolean sparse = ASBGSectionManager.isEnabled();
            check((loaded instanceof PoiColumnTracker) == sparse, "incorrect tracker installation");
            Holder<PoiType> farmer = server.registryAccess().lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE).getOrThrow(PoiTypes.FARMER);
            Holder<PoiType> portal = server.registryAccess().lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE).getOrThrow(PoiTypes.NETHER_PORTAL);
            Method c2meUnload = null;
            try {
                c2meUnload = poi.getClass().getMethod("c2me$unloadPoi", ChunkPos.class);
            } catch (NoSuchMethodException ignored) {
            }
            check(!Boolean.getBoolean("asbg.poiTest.requireC2me") || c2meUnload != null,
                    "C2ME POI unloading was required but its runtime entry point is absent");

            // Every empty column has 108 sections, none of which should occupy storage.
            for (int x = 100; x < 300; x++) {
                BlockPos pos = new BlockPos(x * 16, 0, -1600);
                check(poi.getType(pos).isEmpty(), "unexpected POI in empty column");
                check(poi.getType(pos).isEmpty(), "repeat empty query failed");
                checkKnownEmpty(poi, pos);
            }
            check(data.size() == (sparse ? 0 : 200 * 108), "incorrect empty-column storage size: " + data.size());
            check(rawGet(poi, SectionPos.asLong(100, -23, -100)) == null, "out-of-range section became known-empty");
            check(poi.getType(new BlockPos(1600, -353, -1600)).isEmpty(), "out-of-range getOrLoad failed");

            // An in-progress vanilla marker must not turn an unknown section into known-empty.
            ChunkPos pendingMarker = new ChunkPos(400, -100);
            loaded.add(pendingMarker.toLong());
            check(rawGet(poi, SectionPos.asLong(pendingMarker.x, 0, pendingMarker.z)) == null, "in-progress load was published");
            loaded.remove(pendingMarker.toLong());

            ChunkPos prefetched = new ChunkPos(401, -100);
            poi.prefetch(prefetched).join();
            check(rawGet(poi, SectionPos.asLong(prefetched.x, 0, prefetched.z)) == null, "prefetch published before unpack");
            poi.tick(() -> true);
            checkKnownEmpty(poi, prefetched.getMiddleBlockPosition(0));
            check(data.size() == (sparse ? 0 : 201 * 108), "incorrect prefetched-column storage size");

            // A detached pending future must not republish a column after unload.
            ChunkPos cancelled = new ChunkPos(402, -100);
            var pending = poi.prefetch(cancelled);
            if (c2meUnload != null) {
                c2meUnload.invoke(poi, cancelled);
            } else {
                Long2ObjectMap<?> pendingLoads = field(poi, "pendingLoads");
                pendingLoads.remove(cancelled.toLong());
                loaded.remove(cancelled.toLong());
            }
            pending.join();
            poi.tick(() -> true);
            check(!loaded.contains(cancelled.toLong()), "detached load restored the vanilla marker");
            check(rawGet(poi, SectionPos.asLong(cancelled.x, 0, cancelled.z)) == null, "detached load published known-empty");

            if (System.getProperty("asbg.poiTest.stage", "write").equals("reload")) {
                check(poi.getType(JOB).orElseThrow().is(PoiTypes.FARMER), "job POI not saved");
                check(poi.getType(PORTAL).orElseThrow().is(PoiTypes.NETHER_PORTAL), "portal POI not saved");
                check(poi.getType(REMOVED).isEmpty(), "removed POI reappeared");
                check(poi.getInRange(type -> type.is(PoiTypes.FARMER), JOB, 1, PoiManager.Occupancy.IS_OCCUPIED).count() == 1,
                        "ticket occupancy not saved");
                poi.release(JOB);
            } else {
                // Reset persisted fixtures so the write stage can be rerun in its test directory.
                for (BlockPos pos : new BlockPos[]{JOB, PORTAL, REMOVED}) {
                    if (poi.getType(pos).isPresent()) {
                        poi.remove(pos);
                    }
                }
                poi.add(JOB, farmer);
                poi.add(PORTAL, portal);
                poi.add(REMOVED, farmer);
                poi.remove(REMOVED);
                check(poi.getType(REMOVED).isEmpty(), "POI removal failed");
                check(poi.take(type -> type.is(PoiTypes.FARMER), (type, pos) -> true, JOB, 1).orElseThrow().equals(JOB),
                        "POI acquisition failed");
                poi.release(JOB);
                check(poi.take(type -> type.is(PoiTypes.FARMER), (type, pos) -> true, JOB, 1).isPresent(), "POI reacquisition failed");
            }
            check(data.size() == (sparse ? 3 : loaded.size() * 108), "incorrect populated-column storage size: " + data.size());
            check(poi.findClosest(type -> type.is(PoiTypes.NETHER_PORTAL), PORTAL, 8, PoiManager.Occupancy.ANY).orElseThrow().equals(PORTAL),
                    "Lithium nearby portal lookup failed");
            check(poi.findClosest(type -> true, new BlockPos(1600, 0, -1600), 8, PoiManager.Occupancy.ANY).isEmpty(),
                    "Lithium empty column lookup failed");

            // Exercise the actual C2ME unload method when present, including pure-empty columns.
            ChunkPos empty = new ChunkPos(100, -100);
            if (c2meUnload != null) {
                c2meUnload.invoke(poi, empty);
            } else {
                for (int y = -22; y < 86; y++) {
                    data.remove(SectionPos.asLong(empty.x, y, empty.z));
                }
                loaded.remove(empty.toLong());
            }
            check(!loaded.contains(empty.toLong()), "empty column did not unload");
            check(rawGet(poi, SectionPos.asLong(empty.x, 0, empty.z)) == null, "unloaded column still ready");
            if (poi instanceof RegionBasedStorageSectionExtended<?>) {
                Long2ObjectMap<?> columns = field(poi, "columns");
                check(!columns.containsKey(empty.toLong()), "Lithium empty column survived unload");
            }
            check(poi.getType(empty.getMiddleBlockPosition(0)).isEmpty(), "empty column reload failed");
            checkKnownEmpty(poi, empty.getMiddleBlockPosition(0));

            ChunkPos jobChunk = new ChunkPos(JOB);
            poi.flush(jobChunk);
            if (c2meUnload != null) {
                c2meUnload.invoke(poi, jobChunk);
                check(!loaded.contains(jobChunk.toLong()), "populated column did not unload");
                check(poi.getType(JOB).orElseThrow().is(PoiTypes.FARMER), "populated C2ME reload failed");
            }
            poi.flushAll();
            check(data.size() == (sparse ? 3 : loaded.size() * 108), "incorrect final storage size");
            System.out.println("ASBG_POI_TEST entries=" + data.size() + " initialEmptyColumns=201 loadedColumns=" + loaded.size()
                    + " sectionsPerColumn=108 sparse=" + sparse + " c2me=" + (c2meUnload != null));
            // After saving the fixture, simulate an empty disk result replacing a populated column.
            Method unpack = SectionStorage.class.getDeclaredMethod("unpackChunk", ChunkPos.class,
                    Class.forName(SectionStorage.class.getName() + "$PackedChunk"));
            unpack.setAccessible(true);
            unpack.invoke(poi, jobChunk, null);
            check(poi.getType(JOB).isEmpty() && poi.getType(PORTAL).isEmpty(), "stale POIs survived empty reload");
            checkKnownEmpty(poi, JOB);
        }
    }

    private static void checkKnownEmpty(PoiManager poi, BlockPos pos) throws Exception {
        Object value = rawGet(poi, SectionPos.asLong(pos));
        check(value instanceof Optional<?> optional && optional.isEmpty(), "known-empty read returned null");
        if (poi instanceof RegionBasedStorageSectionExtended<?> lithium) {
            BitSet bits = lithium.lithium$getNonEmptyPOISections(pos.getX() >> 4, pos.getZ() >> 4);
            check(bits.isEmpty(), "empty column has POI bits");
            check(lithium.lithium$getElementAt(SectionPos.asLong(pos)).isEmpty(), "Lithium direct empty read failed");
        }
    }

    private static Object rawGet(PoiManager poi, long section) throws Exception {
        Method get = SectionStorage.class.getDeclaredMethod("get", long.class);
        get.setAccessible(true);
        return get.invoke(poi, section);
    }

    private static <T> T field(Object instance, String name) throws Exception {
        return field(instance, name, SectionStorage.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object instance, String name, Class<?> owner) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(instance);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
