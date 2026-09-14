package net.onixary.sscPrimalstinct.mixin.vanilla;

import com.mojang.datafixers.util.Either;
import net.minecraft.block.BedBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;
import net.onixary.sscPrimalstinct.power.factory.BedSpawnOnlyPower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BedBlock.class)
public abstract class BedSpawnOnlyMixin {
    // Vanilla has already normalized the head position and checked dimension/occupancy.
    @Redirect(method = "onUse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerEntity;trySleep(Lnet/minecraft/util/math/BlockPos;)Lcom/mojang/datafixers/util/Either;"))
    private Either<PlayerEntity.SleepFailureReason, Unit> primalstinct$spawnWithoutSleep(PlayerEntity player, BlockPos pos) {
        if (player instanceof ServerPlayerEntity serverPlayer && BedSpawnOnlyPower.applies(player)) {
            serverPlayer.setSpawnPoint(player.getWorld().getRegistryKey(), pos, player.getYaw(), false, true);
            player.sendMessage(Text.translatable("message.ssc-primalstinct.bed_uncomfortable"), true);
            return Either.right(Unit.INSTANCE);
        }
        return player.trySleep(pos);
    }
}
