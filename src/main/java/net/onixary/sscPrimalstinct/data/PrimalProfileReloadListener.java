package net.onixary.sscPrimalstinct.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 卡02：解析 data/&lt;ns&gt;/primalstinct/{forms/*.json, levels/default.json} 为候选快照。
 * 只做结构与数值校验（未知字段、类型错误、等级表约束）；对 SSC Form / Apoli Power 的引用校验
 * 延迟到 PrimalRosterManager.validateAndSwap（此时注册数据已可用）。
 * 任何文件解析失败 → 候选作废、逐条输出带文件路径的错误、保留上一版运行快照。
 */
public final class PrimalProfileReloadListener implements SimpleSynchronousResourceReloadListener {

    public static final String FORMS_DIR = "primalstinct/forms";
    public static final String LEVELS_DIR = "primalstinct/levels";
    public static final String DIETS_DIR = "primalstinct/diets";
    public static final String LEVELS_DEFAULT_FILE = "default.json";

    private static final Set<String> FORM_KEYS = Set.of(
            "schema_version", "form_id", "selectable", "order", "fallback_form",
            "base_powers", "level_overrides", "instinct_powers", "diet_profile", "sleep_profile");
    private static final Set<String> LEVELS_KEYS = Set.of(
            "schema_version", "max_value", "lock_at_max", "thresholds", "levels");
    private static final Set<String> DIET_KEYS = Set.of("schema_version", "normal", "unsuitable", "forbidden");
    private static final Set<String> DIET_CATEGORY_KEYS = Set.of("instinct_delta", "item_tag");
    private static final Set<String> POWER_LIST_KEYS = Set.of("add", "remove");
    private static final Set<String> LEVEL_OBJECT_KEYS = Set.of("powers");

    @Override
    public Identifier getFabricId() {
        return Identifier.of(SSCPrimalstinct.MOD_ID, "primalstinct_profiles");
    }

    @Override
    public void reload(ResourceManager manager) {
        List<String> errors = new ArrayList<>();
        PrimalLevels levels = parseLevels(manager, errors);
        Map<Identifier, PrimalFormProfile> profiles = new LinkedHashMap<>();

        // 按路径排序保证解析顺序确定，重复 form_id 时报错也可复现
        Map<Identifier, Resource> formFiles = new TreeMap<>(manager.findResources(
                FORMS_DIR, id -> id.getPath().endsWith(".json")));
        for (Map.Entry<Identifier, Resource> entry : formFiles.entrySet()) {
            String sourceFile = entry.getKey().toString();
            JsonObject json = readObject(entry.getValue(), sourceFile, errors);
            if (json == null) {
                continue;
            }
            PrimalFormProfile profile = parseForm(json, levels, sourceFile, errors);
            if (profile == null) {
                continue;
            }
            if (profiles.containsKey(profile.formId)) {
                errors.add(sourceFile + ": 重复的 form_id " + profile.formId
                        + "（已由 " + profiles.get(profile.formId).sourceFile + " 定义）");
                continue;
            }
            profiles.put(profile.formId, profile);
        }

        // 卡08：食性档案
        Map<Identifier, PrimalDiet> diets = new LinkedHashMap<>();
        Map<Identifier, Resource> dietFiles = new TreeMap<>(manager.findResources(
                DIETS_DIR, id -> id.getPath().endsWith(".json")));
        for (Map.Entry<Identifier, Resource> entry : dietFiles.entrySet()) {
            String sourceFile = entry.getKey().toString();
            JsonObject json = readObject(entry.getValue(), sourceFile, errors);
            if (json == null) {
                continue;
            }
            PrimalDiet diet = parseDiet(json, sourceFile, errors);
            if (diet == null) {
                continue;
            }
            diets.put(diet.id(), diet);
        }

        if (!errors.isEmpty()) {
            for (String error : errors) {
                SSCPrimalstinct.LOGGER.error("[primalstinct] 配置解析失败，保留上一版: {}", error);
            }
            PrimalRosterManager.onParseFailed(errors);
            return;
        }
        PrimalRosterManager.onParsed(levels, profiles, diets);
    }

    private @Nullable PrimalDiet parseDiet(JsonObject json, String sourceFile, List<String> errors) {
        try {
            checkKeys(json, DIET_KEYS, sourceFile);
            int schema = intField(json, "schema_version", sourceFile);
            if (schema != PrimalFormProfile.SCHEMA_VERSION) {
                throw new IllegalArgumentException("schema_version 必须为 " + PrimalFormProfile.SCHEMA_VERSION + "，收到 " + schema);
            }
            // diet id 由文件名派生：primalstinct/diets/<id>.json
            String path = sourceFile.substring(sourceFile.indexOf(':') + 1);
            String rawId = path.substring(path.lastIndexOf('/') + 1).replace(".json", "");
            Identifier dietId = Identifier.tryParse(sourceFile.substring(0, sourceFile.indexOf(':')) + ":" + rawId);
            if (dietId == null) {
                throw new IllegalArgumentException("文件名无法派生合法 diet id: " + rawId);
            }
            JsonObject normal = asObject(json.get("normal"), sourceFile + " normal");
            JsonObject unsuitable = asObject(json.get("unsuitable"), sourceFile + " unsuitable");
            JsonObject forbidden = json.has("forbidden") && json.get("forbidden").isJsonObject()
                    ? asObject(json.get("forbidden"), sourceFile + " forbidden") : null;
            return new PrimalDiet(
                    dietId,
                    floatField(normal, "instinct_delta", -Float.MAX_VALUE, Float.MAX_VALUE, sourceFile + " normal"),
                    idField(normal, "item_tag", sourceFile + " normal"),
                    floatField(unsuitable, "instinct_delta", -Float.MAX_VALUE, Float.MAX_VALUE, sourceFile + " unsuitable"),
                    idField(unsuitable, "item_tag", sourceFile + " unsuitable"),
                    forbidden != null ? idField(forbidden, "item_tag", sourceFile + " forbidden") : null,
                    sourceFile);
        } catch (RuntimeException e) {
            errors.add(sourceFile + ": " + e.getMessage());
            return null;
        }
    }

    private PrimalLevels parseLevels(ResourceManager manager, List<String> errors) {
        Map<Identifier, Resource> levelFiles = new TreeMap<>(manager.findResources(
                LEVELS_DIR, id -> id.getPath().endsWith(".json")));
        Resource defaultFile = null;
        String defaultSource = null;
        for (Map.Entry<Identifier, Resource> entry : levelFiles.entrySet()) {
            if (entry.getKey().getPath().equals(LEVELS_DIR + "/" + LEVELS_DEFAULT_FILE)) {
                defaultFile = entry.getValue();
                defaultSource = entry.getKey().toString();
            } else {
                SSCPrimalstinct.LOGGER.warn("[primalstinct] 忽略未知等级表文件（当前仅支持 default.json）: {}", entry.getKey());
            }
        }
        if (defaultFile == null) {
            SSCPrimalstinct.LOGGER.info("[primalstinct] 未找到 levels/default.json，使用内置默认等级表（0/25/50/75/100，L1–L5）");
            return PrimalLevels.defaults();
        }
        JsonObject json = readObject(defaultFile, defaultSource, errors);
        if (json == null) {
            return PrimalLevels.defaults();
        }
        try {
            checkKeys(json, LEVELS_KEYS, defaultSource);
            int schema = intField(json, "schema_version", defaultSource);
            if (schema != PrimalFormProfile.SCHEMA_VERSION) {
                throw new PrimalLevels.BuildError("schema_version 必须为 " + PrimalFormProfile.SCHEMA_VERSION + "，收到 " + schema);
            }
            float maxValue = floatField(json, "max_value", 0.01f, Float.MAX_VALUE, defaultSource);
            boolean lockAtMax = boolField(json, "lock_at_max", defaultSource);
            float[] thresholds = floatArrayField(json, "thresholds", 0.0f, maxValue, defaultSource);
            PrimalLevels.Builder builder = new PrimalLevels.Builder(maxValue, lockAtMax, thresholds);
            JsonObject levelsObj = objectField(json, "levels", defaultSource);
            for (int level = 1; level <= thresholds.length; level++) {
                String key = String.valueOf(level);
                if (!levelsObj.has(key)) {
                    builder.level(level);
                    continue;
                }
                JsonObject levelObj = asObject(levelsObj.get(key), defaultSource + " levels." + key);
                PowerLists powers = parseLevelPowers(levelObj, defaultSource + " levels." + key);
                builder.level(level, powers.add, powers.remove);
            }
            return builder.build();
        } catch (RuntimeException e) {
            errors.add(defaultSource + ": " + e.getMessage());
            return PrimalLevels.defaults();
        }
    }

    private @Nullable PrimalFormProfile parseForm(JsonObject json, PrimalLevels levels, String sourceFile, List<String> errors) {
        try {
            checkKeys(json, FORM_KEYS, sourceFile);
            int schema = intField(json, "schema_version", sourceFile);
            if (schema != PrimalFormProfile.SCHEMA_VERSION) {
                throw new IllegalArgumentException("schema_version 必须为 " + PrimalFormProfile.SCHEMA_VERSION + "，收到 " + schema);
            }
            Identifier formId = idField(json, "form_id", sourceFile);
            boolean selectable = boolField(json, "selectable", sourceFile);
            int order = intField(json, "order", sourceFile);
            Identifier fallbackForm = json.has("fallback_form") && !json.get("fallback_form").isJsonNull()
                    ? idField(json, "fallback_form", sourceFile) : null;

            PowerLists basePowers = parsePowerLists(objectField(json, "base_powers", sourceFile), sourceFile + " base_powers");

            Map<Integer, PrimalLevels.LevelPowers> levelOverrides = new TreeMap<>();
            if (json.has("level_overrides") && json.get("level_overrides").isJsonObject()) {
                JsonObject overrides = json.getAsJsonObject("level_overrides");
                for (Map.Entry<String, JsonElement> entry : overrides.entrySet()) {
                    int level = parsePositiveInt(entry.getKey(), sourceFile + " level_overrides 键");
                    if (level > levels.maxLevel()) {
                        throw new IllegalArgumentException("level_overrides 等级 " + level + " 超出等级表最高级 " + levels.maxLevel());
                    }
                    JsonObject levelObj = asObject(entry.getValue(), sourceFile + " level_overrides." + level);
                    PowerLists powers = parseLevelPowers(levelObj, sourceFile + " level_overrides." + level);
                    for (Identifier id : powers.add) {
                        if (powers.remove.contains(id)) {
                            throw new IllegalArgumentException("level_overrides." + level + " 同级冲突：" + id + " 同时在 add 与 remove");
                        }
                    }
                    levelOverrides.put(level, new PrimalLevels.LevelPowers(powers.add, powers.remove));
                }
            } else if (json.has("level_overrides")) {
                throw new IllegalArgumentException("level_overrides 必须是对象");
            }

            List<Identifier> instinctPowers = idListField(json, "instinct_powers", sourceFile);
            Identifier dietProfile = json.has("diet_profile") && !json.get("diet_profile").isJsonNull()
                    ? idField(json, "diet_profile", sourceFile) : null;
            Identifier sleepProfile = json.has("sleep_profile") && !json.get("sleep_profile").isJsonNull()
                    ? idField(json, "sleep_profile", sourceFile) : null;

            return new PrimalFormProfile(formId, selectable, order, fallbackForm,
                    List.copyOf(basePowers.add), List.copyOf(basePowers.remove),
                    levelOverrides, instinctPowers, dietProfile, sleepProfile, sourceFile);
        } catch (RuntimeException e) {
            errors.add(sourceFile + ": " + e.getMessage());
            return null;
        }
    }

    // ---------- 严格解析辅助：所有错误消息供调用方拼接文件路径 ----------

    private record PowerLists(Set<Identifier> add, Set<Identifier> remove) {
    }

    /** 层级对象形如 {"powers": {"add": [...], "remove": [...]}}。 */
    private PowerLists parseLevelPowers(JsonObject levelObj, String context) {
        checkKeys(levelObj, LEVEL_OBJECT_KEYS, context);
        if (!levelObj.has("powers")) {
            return new PowerLists(new HashSet<>(), new HashSet<>());
        }
        JsonObject powersObj = asObject(levelObj.get("powers"), context + ".powers");
        return parsePowerLists(powersObj, context + ".powers");
    }

    private PowerLists parsePowerLists(JsonObject parent, String context) {
        checkKeys(parent, POWER_LIST_KEYS, context);
        Set<Identifier> add = readIdSet(parent, "add", context);
        Set<Identifier> remove = readIdSet(parent, "remove", context);
        return new PowerLists(add, remove);
    }

    private Set<Identifier> readIdSet(JsonObject parent, String key, String context) {
        if (!parent.has(key)) {
            return new HashSet<>();
        }
        JsonArray array = asArray(parent.get(key), context + "." + key);
        Set<Identifier> result = new HashSet<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(context + "." + key + " 的元素必须是字符串");
            }
            Identifier id = Identifier.tryParse(element.getAsString());
            if (id == null) {
                throw new IllegalArgumentException(context + "." + key + " 存在非法 Identifier: " + element.getAsString());
            }
            result.add(id);
        }
        return result;
    }

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

    private static void checkKeys(JsonObject json, Set<String> allowed, String context) {
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

    private static boolean boolField(JsonObject json, String key, String context) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.get(key).getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(key + " 必须是布尔值（" + context + "）");
        }
        return json.get(key).getAsBoolean();
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

    private static List<Identifier> idListField(JsonObject json, String key, String context) {
        if (!json.has(key)) {
            return List.of();
        }
        JsonArray array = asArray(json.get(key), context + "." + key);
        List<Identifier> result = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(context + "." + key + " 的元素必须是字符串");
            }
            Identifier id = Identifier.tryParse(element.getAsString());
            if (id == null) {
                throw new IllegalArgumentException(context + "." + key + " 存在非法 Identifier: " + element.getAsString());
            }
            result.add(id);
        }
        return result;
    }

    private static float[] floatArrayField(JsonObject json, String key, float min, float max, String context) {
        JsonArray array = asArray(json.get(key), context + "." + key);
        float[] result = new float[array.size()];
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException(context + "." + key + "[" + i + "] 必须是数字");
            }
            result[i] = element.getAsFloat();
            if (result[i] < min || result[i] > max) {
                throw new IllegalArgumentException(context + "." + key + "[" + i + "]=" + result[i] + " 超出 [" + min + ", " + max + "]");
            }
        }
        return result;
    }

    private static JsonObject objectField(JsonObject json, String key, String context) {
        if (!json.has(key)) {
            return new JsonObject();
        }
        return asObject(json.get(key), context + "." + key);
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

    private static int parsePositiveInt(String raw, String context) {
        try {
            int value = Integer.parseInt(raw);
            if (value < 1) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(context + " 必须是正整数等级键，收到 \"" + raw + "\"");
        }
    }
}
