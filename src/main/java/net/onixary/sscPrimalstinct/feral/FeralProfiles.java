package net.onixary.sscPrimalstinct.feral;

import com.google.gson.*;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.ISubForm;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import java.util.HashMap;
import java.util.Map;

/** Server datapack whitelist. Resolved choices are synchronized with each affected player. */
public final class FeralProfiles implements SimpleSynchronousResourceReloadListener {
    public record Profile(Identifier modelForm, Identifier animationForm, float scale, float eyeScale, java.util.Set<Identifier> removePowers, Identifier modelOrigin, Map<Identifier, Identifier> powerAnimations) {
        public Profile(Identifier modelForm, Identifier animationForm, float scale, float eyeScale, java.util.Set<Identifier> removePowers, Identifier modelOrigin) {
            this(modelForm, animationForm, scale, eyeScale, removePowers, modelOrigin, Map.of());
        }
        public Profile(Identifier modelForm, Identifier animationForm, float scale, float eyeScale, java.util.Set<Identifier> removePowers) {
            this(modelForm, animationForm, scale, eyeScale, removePowers, null);
        }
        public Profile(Identifier modelForm, Identifier animationForm) { this(modelForm, animationForm, 1.0f, 0.6f, java.util.Set.of()); }
        public Profile(Identifier modelForm, Identifier animationForm, float scale, float eyeScale) {
            this(modelForm, animationForm, scale, eyeScale, java.util.Set.of());
        }
        public Profile {
            removePowers = java.util.Set.copyOf(removePowers);
            powerAnimations = Map.copyOf(powerAnimations);
            if (!Float.isFinite(scale) || scale <= 0 || !Float.isFinite(eyeScale) || eyeScale <= 0)
                throw new IllegalArgumentException("Feral scale and eye_scale must be finite positive numbers");
        }
    }
    private static Map<Identifier, Profile> profiles = Map.of();
    public static Profile get(Identifier form) {
        // Exact main-form IDs only; even an explicit datapack entry cannot enable a subform.
        var registered = RegPlayerForms.getPlayerForm(form);
        if (registered instanceof ISubForm sub && sub.isSubForm()) return null;
        return profiles.get(form);
    }
    @Override public Identifier getFabricId() { return new Identifier(SSCPrimalstinct.MOD_ID, "feral_presentations"); }

    @Override public void reload(ResourceManager manager) {
        Map<Identifier, Profile> next = new HashMap<>();
        try {
            for (var entry : manager.findResources("feral_presentations", id -> id.getPath().endsWith(".json")).entrySet()) {
                try (var reader = entry.getValue().getReader()) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    Identifier form = new Identifier(json.get("form").getAsString());
                    if (RegPlayerForms.getPlayerForm(form) == null) throw new IllegalArgumentException("Unknown form: " + form);
                    if (json.has("enabled") && !json.get("enabled").getAsBoolean()) continue;
                    Profile profile = new Profile(replacement(json, "model"), replacement(json, "animation"),
                            json.has("scale") ? json.get("scale").getAsFloat() : 1.0f,
                            json.has("eye_scale") ? json.get("eye_scale").getAsFloat() : 0.6f, removedPowers(json),
                            json.has("replace_model") && json.get("replace_model").getAsBoolean() && json.has("model_origin")
                                    ? new Identifier(json.get("model_origin").getAsString()) : null, powerAnimations(json));
                    if (next.putIfAbsent(form, profile) != null) throw new IllegalArgumentException("Duplicate form: " + form);
                }
            }
            profiles = Map.copyOf(next);
        } catch (Exception e) {
            SSCPrimalstinct.LOGGER.error("Invalid feral presentation configuration; retaining previous whitelist", e);
        }
    }

    private static Map<Identifier, Identifier> powerAnimations(JsonObject json) {
        var result = new HashMap<Identifier, Identifier>();
        if (json.has("power_animations")) {
            for (var entry : json.getAsJsonObject("power_animations").entrySet())
                result.put(new Identifier(entry.getKey()), new Identifier(entry.getValue().getAsString()));
        }
        return result;
    }

    private static java.util.Set<Identifier> removedPowers(JsonObject json) {
        var result = new java.util.LinkedHashSet<Identifier>();
        if (json.has("remove_powers")) {
            for (var value : json.getAsJsonArray("remove_powers")) result.add(new Identifier(value.getAsString()));
        }
        return result;
    }

    private static Identifier replacement(JsonObject json, String kind) {
        if (!json.has("replace_" + kind) || !json.get("replace_" + kind).getAsBoolean()) return null;
        if (kind.equals("model") && json.has("model_origin")) {
            if (json.has("model_form")) throw new IllegalArgumentException("Choose model_origin or model_form, not both");
            return null;
        }
        Identifier id = new Identifier(json.get(kind + "_form").getAsString());
        if (RegPlayerForms.getPlayerForm(id) == null) throw new IllegalArgumentException("Unknown " + kind + " form: " + id);
        return id;
    }
}
