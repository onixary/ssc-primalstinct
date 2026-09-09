package net.onixary.sscPrimalstinct.power.factory;
import io.github.apace100.apoli.power.*;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
/** Server evaluates conditions; only the owner's render settings and prey IDs are synchronized. */
public final class InstinctPerceptionPower extends Power {
    public final float codex, palette, signs;
    public final String itemText;
    public final Identifier exemptTag;
    public final float radius;
    public final WanderAiPower sensor;
    /** 注视组：指定实体在半径内（可要求可见）时绿色描边并可贡献本能速率；由 PerceptionSync 周期刷新 watchPresent。 */
    public final @Nullable Identifier watchEntity;
    public final float watchRadius;
    public final boolean watchRequireVisibility;
    public final float watchRatePerSecond;
    /** 最近一次注视扫描是否命中（服务端，速率扫描读取；客户端描边走 PerceptionSync 同步的 ID 集）。 */
    public volatile boolean watchPresent;
    public InstinctPerceptionPower(PowerType<?> type, LivingEntity entity, SerializableData.Instance data) {
        super(type, entity);
        codex = clamp(data.getFloat("codex_chance")); palette = clamp(data.getFloat("palette_chance"));
        signs = clamp(data.getFloat("sign_chance")); itemText = data.getString("item_text");
        exemptTag = data.getId("exempt_item_tag"); radius = Math.max(0, Math.min(128, data.getFloat("target_radius")));
        sensor = new WanderAiPower(type, entity, data.getId("proxy_entity"), 600);
        watchEntity = data.isPresent("watch_entity") ? data.getId("watch_entity") : null;
        watchRadius = Math.max(0, Math.min(128, data.getFloat("watch_radius")));
        watchRequireVisibility = data.getBoolean("watch_require_visibility");
        float rate = data.getFloat("watch_rate_per_second");
        watchRatePerSecond = Float.isFinite(rate) ? rate : 0f;
    }
    private static float clamp(float value) { return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0; }
    public static PowerFactory<?> getFactory() {
        return new PowerFactory<>(new Identifier("ssc-primalstinct", "instinct_perception"),
            new SerializableData().add("codex_chance", SerializableDataTypes.FLOAT, 0f)
                .add("palette_chance", SerializableDataTypes.FLOAT, 0f).add("sign_chance", SerializableDataTypes.FLOAT, 0f)
                .add("item_text", SerializableDataTypes.STRING, "")
                .add("exempt_item_tag", SerializableDataTypes.IDENTIFIER, new Identifier("ssc-primalstinct", "readable_items"))
                .add("target_radius", SerializableDataTypes.FLOAT, 0f)
                .add("proxy_entity", SerializableDataTypes.IDENTIFIER, new Identifier("minecraft", "ocelot"))
                .add("watch_entity", SerializableDataTypes.IDENTIFIER, null)
                .add("watch_radius", SerializableDataTypes.FLOAT, 16f)
                .add("watch_require_visibility", SerializableDataTypes.BOOLEAN, true)
                .add("watch_rate_per_second", SerializableDataTypes.FLOAT, 0f),
            data -> (type, entity) -> new InstinctPerceptionPower(type, entity, data)).allowCondition();
    }
}
