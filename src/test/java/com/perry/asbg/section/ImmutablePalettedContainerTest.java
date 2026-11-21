package com.perry.asbg.section;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import net.minecraft.core.IdMapper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ImmutablePalettedContainerTest {
    private static final String AIR = "air";
    private static final String STONE = "stone";

    @ParameterizedTest(name = "biomes={0}")
    @ValueSource(booleans = {false, true})
    void deserializingAMutableCopyDoesNotChangeTheSharedOriginal(boolean biomes) {
        Strategy<String> strategy = strategy(biomes);
        ImmutablePalettedContainer<String> shared = new ImmutablePalettedContainer<>(AIR, strategy);
        PalettedContainer<String> copy = shared.copy();
        assertNotSame(shared, copy);
        readUniform(copy, STONE, strategy);
        assertEquals(STONE, copy.get(0, 0, 0));
        assertEquals(AIR, shared.get(0, 0, 0));
        int edge = biomes ? 3 : 15;
        assertEquals(AIR, shared.get(edge, edge, edge));
    }

    @ParameterizedTest(name = "biomes={0}")
    @ValueSource(booleans = {false, true})
    void recreatedContainersRemainIndependentAndSharedWritesAreRejected(boolean biomes) {
        Strategy<String> strategy = strategy(biomes);
        ImmutablePalettedContainer<String> shared = new ImmutablePalettedContainer<>(AIR, strategy);
        readUniform(shared.recreate(), STONE, strategy);
        assertEquals(AIR, shared.get(0, 0, 0));
        assertEquals(AIR, shared.getAndSetUnchecked(0, 0, 0, AIR));
        assertThrows(UnsupportedOperationException.class, () -> shared.set(0, 0, 0, STONE));
        assertThrows(UnsupportedOperationException.class, () -> shared.getAndSetUnchecked(0, 0, 0, STONE));
    }

    private static Strategy<String> strategy(boolean biomes) {
        IdMapper<String> ids = new IdMapper<>();
        ids.add(AIR);
        ids.add(STONE);
        return biomes ? Strategy.createForBiomes(ids) : Strategy.createForBlockStates(ids);
    }

    private static void readUniform(PalettedContainer<String> target, String value, Strategy<String> strategy) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new PalettedContainer<>(value, strategy).write(buffer);
            target.read(buffer);
        } finally {
            buffer.release();
        }
    }
}
