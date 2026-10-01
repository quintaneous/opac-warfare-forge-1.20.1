package com.quin.opacwarfare1201.mixin;

import com.quin.opacwarfare1201.config.WarConfig;
import com.quin.opacwarfare1201.war.StrategicCity;
import com.quin.opacwarfare1201.war.WarManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlowingFluid.class)
public abstract class FlowingFluidSiegeMixin {
    @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
    private void gateSiegeFluidSpread(LevelAccessor level, BlockPos pos, BlockState blockState,
                                      Direction direction, FluidState fluidState, CallbackInfo ci) {
        if (!WarConfig.BLOCK_FLUIDS_DURING_CITY_SIEGE.get()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        WarManager manager = WarManager.get(serverLevel.getServer());
        StrategicCity city = manager.cityAtBlock(serverLevel.dimension().location(), pos);
        if (city != null && manager.anyWarForCity(city.id) != null) {
            ci.cancel();
        }
    }
}
