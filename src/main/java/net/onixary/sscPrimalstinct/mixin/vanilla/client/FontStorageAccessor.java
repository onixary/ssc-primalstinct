package net.onixary.sscPrimalstinct.mixin.vanilla.client;
import net.minecraft.client.font.FontStorage;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(FontStorage.class)
public interface FontStorageAccessor {
    @Accessor("charactersByWidth") Int2ObjectMap<IntList> primalstinct$charactersByWidth();
}
