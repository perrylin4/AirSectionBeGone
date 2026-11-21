package com.perry.asbg.test;

import com.perry.asbg.heightmap.HeightmapDeduplication;
import com.perry.asbg.heightmap.SharedHeightmapStorage;
import com.perry.asbg.section.ASBGSectionManager;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.Heightmap;

import static net.minecraft.world.level.levelgen.Heightmap.Types.*;

final class HeightmapIntegrationChecks {
    private static final EnumSet<Heightmap.Types> TYPES = EnumSet.of(
            MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES, OCEAN_FLOOR, WORLD_SURFACE
    );

    private HeightmapIntegrationChecks() {
    }

    static void run(MinecraftServer server) throws ReflectiveOperationException {
        ServerLevel level = server.overworld();
        boolean enabled = ASBGSectionManager.isEnabled();
        Field field = Heightmap.class.getDeclaredField("data");
        field.setAccessible(true);
        int surface = level.getMinY() + 8;
        LevelChunk a = convertedChunk(level, new ChunkPos(10_000, 10_000), surface, field);
        LevelChunk b = convertedChunk(level, new ChunkPos(10_001, 10_000), surface, field);
        BitStorage representative = data(field, map(b, WORLD_SURFACE));
        for (var type : TYPES) {
            check(map(a, type) != map(b, type), "the entire Heightmap was shared");
            expectHeight(a, type, surface + 1);
            check((data(field, map(a, type)) == representative) == enabled, "conversion did not share equal maps");
        }
        check((representative instanceof SharedHeightmapStorage) == enabled, "incorrect storage for the total switch");

        var packetA = new ClientboundLevelChunkPacketData(a);
        var packetB = new ClientboundLevelChunkPacketData(a);
        var saved = SerializableChunkData.copyOf(level, a);
        for (var type : TYPES) {
            check((data(field, map(a, type)) == representative) == enabled, "snapshot detached active heightmap");
        }
        long[] packetRaw = packetA.getHeightmaps().get(WORLD_SURFACE);
        long[] otherPacketRaw = packetB.getHeightmaps().get(WORLD_SURFACE);
        long[] savedRaw = saved.heightmaps().get(WORLD_SURFACE);
        check(packetRaw != otherPacketRaw && packetRaw != savedRaw && otherPacketRaw != savedRaw,
                "snapshot arrays are aliased");
        long[] original = savedRaw.clone();
        Arrays.fill(packetRaw, 0);
        expectHeight(a, WORLD_SURFACE, surface + 1);
        check(Arrays.equals(otherPacketRaw, original), "packet mutation contaminated another packet");

        // Use the real block-write path. Water raises the surface but not the ocean floor.
        a.setBlockState(editPosition(a, surface - 2), Blocks.STONE.defaultBlockState(), 0);
        check((data(field, map(a, WORLD_SURFACE)) == representative) == enabled, "underground write detached the heightmap");
        a.setBlockState(editPosition(a, surface + 1), Blocks.WATER.defaultBlockState(), Block.UPDATE_SKIP_ON_PLACE);
        expectHeight(a, WORLD_SURFACE, surface + 2);
        expectHeight(a, MOTION_BLOCKING, surface + 2);
        expectHeight(a, OCEAN_FLOOR, surface + 1);
        check((data(field, map(a, OCEAN_FLOOR)) == representative) == enabled, "unchanged heightmap detached");
        expectHeight(b, WORLD_SURFACE, surface + 1);
        check(Arrays.equals(savedRaw, original), "block write contaminated the disk snapshot");
        a.setBlockState(editPosition(a, surface + 1), Blocks.OAK_LEAVES.defaultBlockState(), 0);
        expectHeight(a, WORLD_SURFACE, surface + 2);
        expectHeight(a, MOTION_BLOCKING_NO_LEAVES, surface + 1);
        a.setBlockState(editPosition(a, surface + 1), Blocks.AIR.defaultBlockState(), 0);
        expectHeight(a, WORLD_SURFACE, surface + 1);
        HeightmapDeduplication.shareHeightmaps(a);
        check(data(field, map(a, WORLD_SURFACE)) != representative, "a modified map was re-interned");

        // Missing and malformed maps are recomputed before the disk-read hook runs.
        saved.heightmaps().remove(MOTION_BLOCKING_NO_LEAVES);
        saved.heightmaps().put(MOTION_BLOCKING, new long[1]);
        var decoded = SerializableChunkData.parse(level, level.palettedContainerFactory(), saved.write());
        check(decoded != null, "could not parse the chunk snapshot");
        var storageInfo = new RegionStorageInfo("asbg-heightmap-test", level.dimension(), "chunk");
        LevelChunk read = ((ImposterProtoChunk) decoded.read(level, level.getPoiManager(), storageInfo, a.getPos())).getWrapped();
        for (var type : TYPES) {
            expectHeight(read, type, surface + 1);
            check((data(field, map(read, type)) == representative) == enabled, "read did not share the restored map");
        }

        long[] replacement = original.clone();
        Arrays.fill(replacement, 0);
        read.setHeightmap(WORLD_SURFACE, replacement);
        Arrays.fill(replacement, -1);
        expectHeight(read, WORLD_SURFACE, level.getMinY());
        expectHeight(b, WORLD_SURFACE, surface + 1);

        // Getting the backing array preserves live-array semantics before and after sharing.
        LevelChunk exposedBefore = new LevelChunk(level, new ChunkPos(10_002, 10_000));
        Heightmap before = map(exposedBefore, WORLD_SURFACE);
        BitStorage beforeStorage = data(field, before);
        long[] beforeRaw = before.getRawData();
        HeightmapDeduplication.shareHeightmaps(exposedBefore);
        check(data(field, before) == beforeStorage && beforeRaw == before.getRawData(), "pre-exposed storage was replaced");
        Heightmap after = map(b, WORLD_SURFACE);
        long[] afterRaw = after.getRawData();
        afterRaw[0] = 2;
        check(after.getFirstAvailable(0, 0) == level.getMinY() + 2, "the public getter returned a dead copy");
        b.setHeightmap(WORLD_SURFACE, original);
        check(afterRaw == after.getRawData() && Arrays.equals(afterRaw, original), "replacement lost a live array");
        b.setBlockState(editPosition(b, surface + 1), Blocks.STONE.defaultBlockState(), 0);
        check(afterRaw == after.getRawData() && !Arrays.equals(afterRaw, original), "height write lost a live array");
        expectHeight(read, OCEAN_FLOOR, surface + 1);

        checkDiskReload(level, field, enabled);
        System.out.println("ASBG_HEIGHTMAP_TEST_PASS enabled=" + enabled);
    }

    private static LevelChunk convertedChunk(ServerLevel level, ChunkPos pos, int surface, Field field)
            throws IllegalAccessException {
        var proto = new ProtoChunk(pos, UpgradeData.EMPTY, level, level.palettedContainerFactory(), null);
        proto.getSection(proto.getSectionIndex(surface)).setBlockState(8, surface & 15, 8, Blocks.STONE.defaultBlockState());
        Heightmap.primeHeightmaps(proto, TYPES);
        LevelChunk chunk = new LevelChunk(level, proto, null);
        check(!(data(field, proto.getOrCreateHeightmapUnprimed(WORLD_SURFACE)) instanceof SharedHeightmapStorage),
                "a ProtoChunk heightmap was shared");
        return chunk;
    }

    private static void checkDiskReload(ServerLevel level, Field field, boolean enabled) throws IllegalAccessException {
        LevelChunk chunk = level.getChunk(61, 61);
        int top = level.getMaxY() - 8;
        BlockPos pos = editPosition(chunk, top);
        if (System.getProperty("asbg.poiTest.stage", "write").equals("reload")) {
            check(chunk.getBlockState(pos).is(Blocks.GOLD_BLOCK), "edited block did not survive restart");
            expectHeight(chunk, WORLD_SURFACE, top + 1);
            check((data(field, map(chunk, WORLD_SURFACE)) instanceof SharedHeightmapStorage) == enabled,
                    "disk-loaded storage did not follow the share flag");
        } else {
            chunk.setBlockState(pos, Blocks.GOLD_BLOCK.defaultBlockState(), 0);
            expectHeight(chunk, WORLD_SURFACE, top + 1);
        }
    }

    private static BlockPos editPosition(LevelChunk chunk, int y) {
        return chunk.getPos().getMiddleBlockPosition(y);
    }

    private static Heightmap map(LevelChunk chunk, Heightmap.Types type) {
        return chunk.getOrCreateHeightmapUnprimed(type);
    }

    private static BitStorage data(Field field, Heightmap heightmap) throws IllegalAccessException {
        return (BitStorage) field.get(heightmap);
    }

    private static void expectHeight(LevelChunk chunk, Heightmap.Types type, int expected) {
        int height = map(chunk, type).getFirstAvailable(8, 8);
        check(height == expected, type + " at " + chunk.getPos() + " was " + height + ", expected " + expected);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
