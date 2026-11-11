package com.perry.asbg.section;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public enum AirKind {
    AIR(Blocks.AIR.defaultBlockState()),
    CAVE_AIR(Blocks.CAVE_AIR.defaultBlockState()),
    VOID_AIR(Blocks.VOID_AIR.defaultBlockState());

    private final BlockState state;

    AirKind(BlockState state) {
        this.state = state;
    }

    public BlockState state() {
        return state;
    }

    public static AirKind fromState(BlockState state) {
        for (AirKind kind : values()) {
            if (state == kind.state) {
                return kind;
            }
        }
        return null;
    }
}
