package com.perry.asbg.section;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

public final class SectionRefoldScheduler {
    public static final int REFOLD_DELAY_TICKS = 200;
    public static final int MAX_REFOLDS_PER_WORLD_TICK = 64;
    private static final int MAX_QUEUE_POLLS_PER_WORLD_TICK = 256;

    private static final Map<ServerLevel, WorldQueue> WORLD_QUEUES = new IdentityHashMap<>();

    private SectionRefoldScheduler() {
    }

    public static void schedule(ServerLevel level, long sectionPos) {
        WorldQueue queue = WORLD_QUEUES.computeIfAbsent(level, ignored -> new WorldQueue());
        if (queue.dueTimes.containsKey(sectionPos)) {
            return;
        }

        long dueTime = level.getGameTime() + REFOLD_DELAY_TICKS;
        queue.dueTimes.put(sectionPos, dueTime);
        queue.candidates.add(new Candidate(sectionPos, dueTime));
    }

    public static void cancel(ServerLevel level, long sectionPos) {
        WorldQueue queue = WORLD_QUEUES.get(level);
        if (queue != null) {
            queue.dueTimes.remove(sectionPos);
        }
    }

    public static void tick(ServerLevel level) {
        if (!AirVoidSectionManager.isEnabled()) {
            return;
        }

        WorldQueue queue = WORLD_QUEUES.get(level);
        if (queue == null) {
            return;
        }

        long gameTime = level.getGameTime();
        int folded = 0;
        int polled = 0;

        while (folded < MAX_REFOLDS_PER_WORLD_TICK
                && polled < MAX_QUEUE_POLLS_PER_WORLD_TICK
                && !queue.candidates.isEmpty()) {
            Candidate candidate = queue.candidates.peek();
            if (candidate.dueTime() > gameTime) {
                break;
            }

            queue.candidates.poll();
            polled++;

            Long currentDueTime = queue.dueTimes.get(candidate.sectionPos());
            if (currentDueTime == null || currentDueTime != candidate.dueTime()) {
                continue;
            }
            queue.dueTimes.remove(candidate.sectionPos());

            int chunkX = SectionPos.x(candidate.sectionPos());
            int chunkZ = SectionPos.z(candidate.sectionPos());
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk == null) {
                continue;
            }

            int index = chunk.getSectionIndexFromSectionY(SectionPos.y(candidate.sectionPos()));
            if (index >= 0 && index < chunk.getSections().length
                    && AirVoidSectionManager.canonicalizeStates(level, chunk.getSection(index))) {
                folded++;
            }
        }

        if (queue.dueTimes.isEmpty() && queue.candidates.isEmpty()) {
            WORLD_QUEUES.remove(level);
        }
    }

    public static void clear(ServerLevel level) {
        WORLD_QUEUES.remove(level);
    }

    public static void clearAll() {
        WORLD_QUEUES.clear();
    }

    private record Candidate(long sectionPos, long dueTime) implements Comparable<Candidate> {
        @Override
        public int compareTo(Candidate other) {
            return Long.compare(dueTime, other.dueTime);
        }
    }

    private static final class WorldQueue {
        private final Map<Long, Long> dueTimes = new HashMap<>();
        private final PriorityQueue<Candidate> candidates = new PriorityQueue<>();
    }
}
