package com.ishland.c2me.natives.mixin;

import com.ishland.c2me.natives.common.NativeInterface;
import com.ishland.c2me.natives.common.NativeStruct;
import com.ishland.c2me.natives.common.NativesUtils;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.util.math.noise.OctavePerlinNoiseSampler;
import net.minecraft.util.math.noise.PerlinNoiseSampler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = OctavePerlinNoiseSampler.class, priority = 1200)
public class MixinOctavePerlinNoiseSampler implements NativeStruct {

    @Shadow @Final private double lacunarity;

    @Shadow @Final private double persistence;

    @Shadow @Final private PerlinNoiseSampler[] octaveSamplers;

    @Shadow @Final private DoubleList amplitudes;

    private long octaveSamplerDataPointer = 0L;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo info) {
        this.octaveSamplerDataPointer = NativesUtils.createOctaveSamplerPointer(this, octaveSamplers, null, amplitudes.toDoubleArray(), lacunarity, persistence);
        // native memory management already handled by NativesUtils#createOctaveSamplerPointer
    }

    /**
     * @author ishland
     * @reason use native method
     */
    @Overwrite
    public double sample(double x, double y, double z) {
        return NativeInterface.perlinSampleOctave(octaveSamplerDataPointer, x, y, z);
    }

    @Override
    public long getNativePointer() {
        return this.octaveSamplerDataPointer;
    }
}