package com.ishland.c2me.opts.scheduling.mixin.task_scheduling;

import net.minecraft.util.thread.ThreadExecutor;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ThreadExecutor.class)
public abstract class MixinThreadExecutor<R extends Runnable> {

    @Shadow @Final private static Logger LOGGER;

    @Shadow public abstract String getName();

    @Redirect(method = "runTask", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/thread/ThreadExecutor;executeTask(Ljava/lang/Runnable;)V"))
    private void redirectExecuteTask(ThreadExecutor<R> threadExecutor, R task) {
        try {
            task.run();
        } catch (Throwable t) {
            LOGGER.error("Error executing task on {}", this.getName(), t);
        }
    }

}
