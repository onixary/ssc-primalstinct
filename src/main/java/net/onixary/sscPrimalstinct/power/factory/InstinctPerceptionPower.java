package net.onixary.sscPrimalstinct.power.factory;
import io.github.apace100.apoli.power.*;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
/** Server evaluates conditions; only the owner's render settings and prey IDs are synchronized. */
public final class InstinctPerceptionPower extends Power {
    public final float codex, palette, signs;
    public final String itemText;
    public final Identifier exemptTag;
    public final float radius;
    public final WanderAiPower sensor;
    public InstinctPerceptionPower(PowerType<?> type, LivingEntity entity, SerializableData.Instance data) {
        super(type, entity);
        codex = clamp(data.getFloat("codex_chance")); palette = clamp(data.getFloat("palette_chance"));
        signs = clamp(data.getFloat("sign_chance")); itemText = data.getString("item_text");
        exemptTag = data.getId("exempt_item_tag"); radius = Math.max(0, Math.min(128, data.getFloat("target_radius")));
        sensor = new WanderAiPower(type, entity, data.getId("proxy_entity"), 600);
    }
    private static float clamp(float value) { return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0; }
    public static PowerFactory<?> getFactory() {
        return new PowerFactory<>(new Identifier("ssc-primalstinct", "instinct_perception"),
            new SerializableData().add("codex_chance", SerializableDataTypes.FLOAT, 0f)
                .add("palette_chance", SerializableDataTypes.FLOAT, 0f).add("sign_chance", SerializableDataTypes.FLOAT, 0f)
                .add("item_text", SerializableDataTypes.STRING, "")
                .add("exempt_item_tag", SerializableDataTypes.IDENTIFIER, new Identifier("ssc-primalstinct", "readable_items"))
                .add("target_radius", SerializableDataTypes.FLOAT, 0f)
                .add("proxy_entity", SerializableDataTypes.IDENTIFIER, new Identifier("minecraft", "ocelot")),
            data -> (type, entity) -> new InstinctPerceptionPower(type, entity, data)).allowCondition();
    }
}
