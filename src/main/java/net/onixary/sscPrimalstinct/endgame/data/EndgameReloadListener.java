package net.onixary.sscPrimalstinct.endgame.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.endgame.EndgameRules;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 眷属实现02：解析 data/&lt;ns&gt;/endgame/{rituals/default.json, variants/*.json} 为候选快照。
 * 复用名单数据包的“候选解析→引用验证→原子替换”流程：
 * 这里只做结构与数值校验；对 SSC Form / 物品注册表的引用验证在
 * EndgameRosterManager.validateAndSwap（SERVER_STARTED / reload 后，注册数据可用时）。
 * 任何文件解析失败 → 候选作废、保留上一版运行快照。
 */
public final class EndgameReloadListener implements SimpleSynchronousResourceReloadListener {

    public static final String RITUALS_DIR = "endgame/rituals";
    public static final String RITUALS_DEFAULT_FILE = "endgame/rituals/default.json";
    public static final String VARIANTS_DIR = "endgame/variants";

    private static final java.util.Set<String> RITUAL_KEYS = java.util.Set.of(
            "schema_version", "ritual_id", "structure_id", "offerings",
            "remnant_drop_chance", "remnant_target_tag");
    private static final java.util.Set<String> OFFERING_KEYS = java.util.Set.of("item", "count", "weight");
    private static final java.util.Set<String> VARIANT_KEYS = java.util.Set.of(
            "schema_version", "source_form", "target_form", "completion_info_key");

    @Override
    public Identifier getFabricId() {
        return Identifier.of(SSCPrimalstinct.MOD_ID, "endgame_rituals");
    }

    @Override
    public void reload(ResourceManager manager) {
        List<String> errors = new ArrayList<>();
        RitualParseResult ritual = parseRitual(manager, errors);
        Map<Identifier, EndgameVariantMapping> variants = parseVariants(manager, errors);

        if (!errors.isEmpty()) {
            for (String error : errors) {
                SSCPrimalstinct.LOGGER.error("[primalstinct] 终局配置解析失败，保留上一版: {}", error);
            }
            EndgameRosterManager.onParseFailed(errors);
            return;
        }
        EndgameRitualConfig config = new EndgameRitualConfig(
                EndgameRules.RITUAL_SCHEMA_VERSION,
                ritual.ritualId(), ritual.structureId(),
                ritual.offerings(), ritual.dropChance(), ritual.targetTag(),
                variants);
        EndgameRosterManager.onParsed(config);
    }

    // ---------- rituals/default.json ----------

    private record RitualParseResult(Identifier ritualId, Identifier structureId,
                                     List<EndgameRitualConfig.OfferingEntry> offerings,
                                     float dropChance, @Nullable Identifier targetTag) {
    }

    private RitualParseResult parseRitual(ResourceManager manager, List<String> errors) {
        // 仅支持 default.json；多文件时按路径序取第一个并提示
        Map<Identifier, Resource> files = new TreeMap<>(manager.findResources(
                RITUALS_DIR, id -> id.getPath().endsWith(".json")));
        Resource defaultFile = null;
        String source = null;
        for (Map.Entry<Identifier, Resource> entry : files.entrySet()) {
            if (entry.getKey().getPath().equals(RITUALS_DEFAULT_FILE)) {
                defaultFile = entry.getValue();
                source = entry.getKey().toString();
            } else {
                SSCPrimalstinct.LOGGER.warn("[primalstinct] 忽略未知终局仪式文件（当前仅支持 default.json）: {}", entry.getKey());
            }
        }
        if (defaultFile == null) {
            // 无终局数据包：候选为空配置（终局功能停用，但主流程不受影响）
            return new RitualParseResult(Identifier.of(SSCPrimalstinct.MOD_ID, "default"),
                    Identifier.of(SSCPrimalstinct.MOD_ID, "primal_altar"),
                    List.of(), 0.0f, null);
        }
        JsonObject json = readObject(defaultFile, source, errors);
        if (json == null) {
            return null;
        }
        try {
            checkKeys(json, RITUAL_KEYS, source);
            int schema = intField(json, "schema_version", source);
            if (schema != EndgameRules.RITUAL_SCHEMA_VERSION) {
                throw new IllegalArgumentException("schema_version 必须为 " + EndgameRules.RITUAL_SCHEMA_VERSION + "，收到 " + schema);
            }
            Identifier ritualId = idField(json, "ritual_id", source);
            Identifier structureId = idField(json, "structure_id", source);
            float dropChance = floatField(json, "remnant_drop_chance", 0.0f, 1.0f, source);
            Identifier targetTag = json.has("remnant_target_tag") && !json.get("remnant_target_tag").isJsonNull()
                    ? idField(json, "remnant_target_tag", source) : null;

            JsonArray array = asArray(json.get("offerings"), source + ".offerings");
            List<EndgameRitualConfig.OfferingEntry> offerings = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                JsonObject entry = asObject(array.get(i), source + ".offerings[" + i + "]");
                checkKeys(entry, OFFERING_KEYS, source + ".offerings[" + i + "]");
                Identifier item = idField(entry, "item", source + ".offerings[" + i + "]");
                int count = intField(entry, "count", source + ".offerings[" + i + "]");
                int weight = intField(entry, "weight", source + ".offerings[" + i + "]");
                if (count < 1) {
                    throw new IllegalArgumentException("offerings[" + i + "].count 必须 >= 1");
                }
                if (weight < 1) {
                    throw new IllegalArgumentException("offerings[" + i + "].weight 必须 >= 1");
                }
                offerings.add(new EndgameRitualConfig.OfferingEntry(item, count, weight));
            }
            if (offerings.size() < EndgameRules.PEDESTAL_COUNT) {
                throw new IllegalArgumentException("供物候选数量(" + offerings.size() + ")少于基座数量("
                        + EndgameRules.PEDESTAL_COUNT + ")，无法无放回抽取");
            }
            return new RitualParseResult(ritualId, structureId, offerings, dropChance, targetTag);
        } catch (RuntimeException e) {
            errors.add(source + ": " + e.getMessage());
            return null;
        }
    }

    // ---------- variants/*.json ----------

    private Map<Identifier, EndgameVariantMapping> parseVariants(ResourceManager manager, List<String> errors) {
        Map<Identifier, EndgameVariantMapping> variants = new LinkedHashMap<>();
        Map<Identifier, Resource> files = new TreeMap<>(manager.findResources(
                VARIANTS_DIR, id -> id.getPath().endsWith(".json")));
        for (Map.Entry<Identifier, Resource> entry : files.entrySet()) {
            String sourceFile = entry.getKey().toString();
            JsonObject json = readObject(entry.getValue(), sourceFile, errors);
            if (json == null) {
                continue;
            }
            try {
                checkKeys(json, VARIANT_KEYS, sourceFile);
                int schema = intField(json, "schema_version", sourceFile);
                if (schema != EndgameRules.VARIANT_SCHEMA_VERSION) {
                    throw new IllegalArgumentException("schema_version 必须为 " + EndgameRules.VARIANT_SCHEMA_VERSION + "，收到 " + schema);
                }
                Identifier source = idField(json, "source_form", sourceFile);
                Identifier target = idField(json, "target_form", sourceFile);
                if (source.equals(target)) {
                    throw new IllegalArgumentException("禁止自映射：" + source);
                }
                String infoKey = json.has("completion_info_key") && json.get("completion_info_key").isJsonPrimitive()
                        ? json.get("completion_info_key").getAsString() : null;
                if (infoKey == null || infoKey.isBlank()) {
                    throw new IllegalArgumentException("completion_info_key 必须是非空字符串");
                }
                if (variants.containsKey(source)) {
                    throw new IllegalArgumentException("重复的 source_form " + source
                            + "（已由 " + variants.get(source).sourceFile + " 定义）");
                }
                variants.put(source, new EndgameVariantMapping(source, target, infoKey, sourceFile));
            } catch (RuntimeException e) {
                errors.add(sourceFile + ": " + e.getMessage());
            }
        }
        return variants;
    }

    // ---------- 严格解析辅助（与 PrimalProfileReloadListener 同风格） ----------

    private static @Nullable JsonObject readObject(Resource resource, String sourceFile, List<String> errors) {
        try {
            JsonElement element = JsonParser.parseString(new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            if (!element.isJsonObject()) {
                errors.add(sourceFile + ": 顶层必须是 JSON 对象");
                return null;
            }
            return element.getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            errors.add(sourceFile + ": JSON 读取/解析失败 - " + e.getMessage());
            return null;
        }
    }

    private static void checkKeys(JsonObject json, java.util.Set<String> allowed, String context) {
        for (String key : json.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("未知字段 \"" + key + "\"（" + context + "）");
            }
        }
    }

    private static int intField(JsonObject json, String key, String context) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.get(key).getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " 必须是数字（" + context + "）");
        }
        return json.get(key).getAsInt();
    }

    private static float floatField(JsonObject json, String key, float min, float max, String context) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.get(key).getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " 必须是数字（" + context + "）");
        }
        float value = json.get(key).getAsFloat();
        if (value < min || value > max) {
            throw new IllegalArgumentException(key + "=" + value + " 超出 [" + min + ", " + max + "]（" + context + "）");
        }
        return value;
    }

    private static Identifier idField(JsonObject json, String key, String context) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.get(key).getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " 必须是字符串 Identifier（" + context + "）");
        }
        Identifier id = Identifier.tryParse(json.get(key).getAsString());
        if (id == null) {
            throw new IllegalArgumentException(key + " 是非法 Identifier: " + json.get(key).getAsString() + "（" + context + "）");
        }
        return id;
    }

    private static JsonObject asObject(JsonElement element, String context) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(context + " 必须是对象");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray asArray(JsonElement element, String context) {
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException(context + " 必须是数组");
        }
        return element.getAsJsonArray();
    }
}
