package net.onixary.sscPrimalstinct.mixin.ssc;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.blocks.FormAttunerBlock;
import net.onixary.sscPrimalstinct.adapter.ssc.SSCAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FormAttunerBlock.class, remap = false)
public abstract class FormAttunerBlockMixin {
    @Inject(method = "openScreen", at = @At("HEAD"), cancellable = true)
    private void primalstinct$blockAttunement(World world, BlockPos pos, ServerPlayerEntity player, CallbackInfo ci) {
        if (SSCAdapter.blockPrimalAttunement(player)) ci.cancel();
    }
}
