package com.ishland.c2me.opts.chunk_access.mixin.asm;

import net.minecraft.server.world.ServerChunkManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = {
        ServerChunkManager.class
})
public class ASMTargets {
}
