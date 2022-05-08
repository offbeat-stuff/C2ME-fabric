package com.ishland.c2me.natives.mixin.density_functions;

import com.google.common.collect.ImmutableMap;
import com.ishland.c2me.natives.common.CompiledDensityFunctionArg;
import com.ishland.c2me.natives.common.CompiledDensityFunctionImpl;
import com.ishland.c2me.natives.common.DensityFunctionUtils;
import com.ishland.c2me.natives.common.NativeInterface;
import com.ishland.c2me.natives.common.NativeMemoryTracker;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DensityFunctionTypes.class_6917.class)
public abstract class MixinDensityFunctionTypesFullOperation implements DensityFunctionTypes.class_6932, CompiledDensityFunctionImpl {

    @Shadow
    public abstract DensityFunctionTypes.Operation.Type type();

    @Shadow
    @Final
    private DensityFunction argument1;
    @Shadow
    @Final
    private DensityFunction argument2;
    @Shadow @Final private DensityFunctionTypes.Operation.Type type;
    @Unique
    private long pointer;

    @Unique
    private String errorMessage = null;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo info) {
        if (!DensityFunctionUtils.isCompiled(this.argument1, this.argument2)) {
            if (DensityFunctionUtils.DEBUG) {
                this.errorMessage = DensityFunctionUtils.getErrorMessage(
                        this,
                        ImmutableMap.of(
                                "argument1", this.argument1,
                                "argument2", this.argument2
                        )
                );
                assert this.errorMessage != null;
                System.err.println("Failed to compile density function: operation_full %s".formatted(this));
                System.err.println(DensityFunctionUtils.indent(this.errorMessage, false));
            }
            return;
        }

        this.pointer = NativeInterface.createDFIOperationFull(
                DensityFunctionUtils.mapOperationToNative(this.type()),
                ((CompiledDensityFunctionImpl) this.argument1).getDFIPointer(),
                ((CompiledDensityFunctionImpl) this.argument2).getDFIPointer()
        );
        NativeMemoryTracker.registerAllocatedMemory(
                this,
                NativeInterface.SIZEOF_density_function_data + NativeInterface.SIZEOF_dfi_operation_full_data,
                this.pointer
        );
    }

    @Override
    public double sample(NoisePos pos) {
        if (this.pointer != 0) {
            return NativeInterface.dfiBindingsSingleOp(this.pointer, pos.blockX(), pos.blockY(), pos.blockZ());
        } else {
            // TODO [VanillaCopy]
            double d = this.argument1.sample(pos);

            return switch(this.type) {
                case ADD -> d + this.argument2.sample(pos);
                case MAX -> d > this.argument2.maxValue() ? d : Math.max(d, this.argument2.sample(pos));
                case MIN -> d < this.argument2.minValue() ? d : Math.min(d, this.argument2.sample(pos));
                case MUL -> d == 0.0 ? 0.0 : d * this.argument2.sample(pos);
            };
        }
    }

    @Override
    public void method_40470(double[] ds, class_6911 arg) {
        if (arg instanceof CompiledDensityFunctionArg dfa && dfa.getDFAPointer() != 0 && this.pointer != 0) {
            NativeInterface.dfiBindingsMultiOp(this.pointer, dfa.getDFAPointer(), ds);
        } else {
            // TODO [VanillaCopy]
            this.argument1.method_40470(ds, arg);
            switch(this.type) {
                case ADD:
                    double[] es = new double[ds.length];
                    this.argument2.method_40470(es, arg);

                    for(int i = 0; i < ds.length; ++i) {
                        ds[i] += es[i];
                    }
                    break;
                case MAX:
                    double e = this.argument2.maxValue();

                    for(int k = 0; k < ds.length; ++k) {
                        double f = ds[k];
                        ds[k] = f > e ? f : Math.max(f, this.argument2.sample(arg.method_40477(k)));
                    }
                    break;
                case MIN:
                    double e1 = this.argument2.minValue();

                    for(int k = 0; k < ds.length; ++k) {
                        double f = ds[k];
                        ds[k] = f < e1 ? f : Math.min(f, this.argument2.sample(arg.method_40477(k)));
                    }
                    break;
                case MUL:
                    for(int j = 0; j < ds.length; ++j) {
                        double d = ds[j];
                        ds[j] = d == 0.0 ? 0.0 : d * this.argument2.sample(arg.method_40477(j));
                    }
            }
        }
    }

    @Override
    public long getDFIPointer() {
        return this.pointer;
    }

    @Nullable
    @Override
    public String getCompilationFailedReason() {
        return this.errorMessage;
    }

}