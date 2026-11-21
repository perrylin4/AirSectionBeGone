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
        // This runs on the block-write path; values() would clone an array on every call.
        if (state == AIR.state) {
            return AIR;
        }
        if (state == CAVE_AIR.state) {
            return CAVE_AIR;
        }
        if (state == VOID_AIR.state) {
            return VOID_AIR;
        }
        return null;
    }
}
