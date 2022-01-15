package com.ishland.c2me.mixin.optimization.chunkscheduling.idle_tasks;

import com.ishland.c2me.common.optimization.chunkscheduling.idle_tasks.IThreadedAnvilChunkStorage;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Chunk.class)
public abstract class MixinChunk {

    @Shadow protected volatile boolean needsSaving;

    @Shadow public abstract ChunkPos getPos();

    @Inject(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/Chunk;needsSaving:Z", shift = At.Shift.AFTER))
    private void onSetShouldSave(CallbackInfo ci) {
        //noinspection ConstantConditions
        if (this.needsSaving && (Object) this instanceof WorldChunk worldChunk) {
            if (worldChunk.getWorld() instanceof ServerWorld serverWorld) {
                ((IThreadedAnvilChunkStorage) serverWorld.getChunkManager().threadedAnvilChunkStorage).enqueueDirtyChunkPosForAutoSave(this.getPos());
            }
        }
    }

}
