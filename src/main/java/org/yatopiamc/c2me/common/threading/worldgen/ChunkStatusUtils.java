package org.yatopiamc.c2me.common.threading.worldgen;

import com.ibm.asyncutil.locks.AsyncLock;
import com.ibm.asyncutil.locks.AsyncNamedLock;
import com.ibm.asyncutil.util.Combinators;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import org.jetbrains.annotations.NotNull;
import org.yatopiamc.c2me.common.threading.GlobalExecutors;
import org.yatopiamc.c2me.common.util.AsyncCombinedLock;
import org.yatopiamc.c2me.common.util.AsyncNamedLockDelegateAsyncLock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ChunkStatusUtils {

    public static ChunkStatusThreadingType getThreadingType(final ChunkStatus status) {
        switch (status.getId()) {
            case "structure_starts":
            case "structure_references":
            case "biomes":
            case "noise":
            case "surface":
            case "carvers":
            case "liquid_carvers":
            case "spawn":
            case "heightmaps":
                return ChunkStatusThreadingType.PARALLELIZED;
            case "features":
                return ChunkStatusThreadingType.SINGLE_THREADED;
            default:
                return ChunkStatusThreadingType.AS_IS;
        }
    }

    public static <T> CompletableFuture<T> runChunkGenWithLock(ChunkPos target, int radius, AsyncNamedLock<ChunkPos> chunkLock, Supplier<CompletableFuture<T>> action) {
        List<AsyncLock> acquiredLocks = new ArrayList<>((radius + 1) * (radius + 1));
        for (int x = target.x - radius; x <= target.x + radius; x++)
            for (int z = target.z - radius; z <= target.z + radius; z++)
                acquiredLocks.add(new AsyncNamedLockDelegateAsyncLock<>(chunkLock, new ChunkPos(x, z)));

        return new AsyncCombinedLock(new HashSet<>(acquiredLocks)).getFuture().thenComposeAsync(lockToken -> {
            final CompletableFuture<T> future = action.get();
            future.thenRun(lockToken::releaseLock);
            return future;
        }, GlobalExecutors.scheduler);
    }

}
