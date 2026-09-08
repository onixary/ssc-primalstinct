package net.onixary.sscPrimalstinct.mixin.vanilla.client;
import net.minecraft.text.Style;
import net.minecraft.client.font.*;
import net.minecraft.util.math.MathHelper;
import net.onixary.sscPrimalstinct.client.network.PerceptionClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
/** Same vanilla glyph pool, but deterministic selection for our own scrambling only. */
@Mixin(targets = "net.minecraft.client.font.TextRenderer$Drawer")
public abstract class InstinctTextRendererMixin {
    @Unique private Integer primalstinct$seed;
    @Unique private int primalstinct$original;
    @ModifyVariable(method = "accept", at = @At("HEAD"), argsOnly = true)
    private Style primalstinct$glyph(Style style, int index, Style original, int codePoint) {
        primalstinct$seed = PerceptionClientState.selectedGlyphSeed(index, codePoint);
        primalstinct$original = codePoint;
        return primalstinct$seed == null ? style : style.withObfuscated(true);
    }
    @Redirect(method = "accept", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/FontStorage;getObfuscatedGlyphRenderer(Lnet/minecraft/client/font/Glyph;)Lnet/minecraft/client/font/GlyphRenderer;"))
    private GlyphRenderer primalstinct$stableGlyph(FontStorage font, Glyph glyph) {
        if (primalstinct$seed == null) return font.getObfuscatedGlyphRenderer(glyph);
        var candidates = ((FontStorageAccessor) font).primalstinct$charactersByWidth()
                .get(MathHelper.ceil(glyph.getAdvance(false)));
        if (candidates == null || candidates.isEmpty()) return font.getGlyphRenderer(primalstinct$original);
        int position = Math.floorMod(primalstinct$seed, candidates.size());
        int replacement = candidates.getInt(position);
        if (replacement == primalstinct$original && candidates.size() > 1)
            replacement = candidates.getInt((position + 1) % candidates.size());
        return font.getGlyphRenderer(replacement);
    }
}
