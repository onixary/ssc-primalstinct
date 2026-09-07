package net.onixary.sscPrimalstinct.mixin.ssc;

import net.onixary.shapeShifterCurseFabric.player_form.utils.InstinctUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.HashMap;
import java.util.UUID;

@Mixin(value = InstinctUtils.class, remap = false)
public interface InstinctUtilsAccessor {
    @Accessor("playerInstinctRate")
    static HashMap<UUID, Float> primalstinct$rates() { throw new AssertionError(); }
}
