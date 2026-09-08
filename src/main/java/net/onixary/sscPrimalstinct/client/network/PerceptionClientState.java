package net.onixary.sscPrimalstinct.client.network;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.*;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.network.PerceptionSync;
import java.util.*;
public final class PerceptionClientState {
    private static float codex, palette, signs;
    private static Integer signSeed;
    private static String itemText = "";
    private static Set<Identifier> tags = Set.of();
    private static Set<Integer> targets = Set.of();
    private static Identifier dimension;
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PerceptionSync.ID, (client, handler, buf, sender) -> {
            Identifier world = buf.readIdentifier();
            float c = buf.readFloat(), p = buf.readFloat(), s = buf.readFloat(); String text = buf.readString();
            Set<Identifier> exemptions = new HashSet<>(); int count = buf.readVarInt();
            for (int i = 0; i < count; i++) exemptions.add(buf.readIdentifier());
            Set<Integer> ids = new HashSet<>(); count = buf.readVarInt();
            for (int i = 0; i < count; i++) ids.add(buf.readVarInt());
            client.execute(() -> { dimension = world; codex = c; palette = p; signs = s;
                itemText = text; tags = exemptions; targets = ids; });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }
    private static boolean active() {
        var client = MinecraftClient.getInstance();
        return client.isOnThread() && client.player != null && client.player.isAlive() && client.world != null && ClientPrimalstinctState.managed()
                && client.world.getRegistryKey().getValue().equals(dimension);
    }
    public static boolean outlined(int id) { return active() && targets.contains(id); }
    public static String replacement(ItemStack stack) {
        if (!active() || itemText.isEmpty() || stack.isOf(net.onixary.sscPrimalstinct.items.RegPrimalstinctItems.SEDATIVE_FRAGMENT)) return null;
        for (var tag : tags) if (stack.isIn(TagKey.of(RegistryKeys.ITEM, tag))) return null;
        return itemText;
    }
    public static float screenChance() {
        if (!active()) return 0;
        var screen = MinecraftClient.getInstance().currentScreen;
        if (screen == null) return 0;
        String name = screen.getClass().getName();
        if (!name.startsWith("net.onixary.shapeShifterCurseFabric.custom_ui.")) return 0;
        if (name.contains("BookOfShapeShifter") || name.contains("Codex") || name.endsWith(".DetailScreen")) return codex;
        if (name.contains("FormColorSelect")) return palette;
        return 0;
    }
    public static OrderedText scrambleSign(OrderedText text) { return scramble(text, active() ? signs : 0); }
    public static OrderedText scramble(OrderedText text, float chance) {
        if (chance <= 0) return text;
        return visitor -> text.accept((index, style, codePoint) -> {
            Integer previous = signSeed;
            signSeed = net.onixary.sscPrimalstinct.util.TextScrambling.selected(index, codePoint, chance)
                    ? glyphSeed(index, codePoint) : null;
            try { return visitor.accept(index, style, codePoint); }
            finally { signSeed = previous; }
        });
    }
    public static Integer selectedGlyphSeed(int index, int codePoint) {
        if (signSeed != null) return signSeed;
        return net.onixary.sscPrimalstinct.util.TextScrambling.selected(index, codePoint, screenChance())
                ? glyphSeed(index, codePoint) : null;
    }
    private static int glyphSeed(int index, int codePoint) {
        int seed = index * 0x9e3779b9 ^ codePoint * 0x85ebca6b;
        seed ^= seed >>> 16; seed *= 0x7feb352d; return seed ^ (seed >>> 15);
    }
    public static void clear() { dimension = null; codex = palette = signs = 0; itemText = ""; tags = Set.of(); targets = Set.of(); }
}
