package net.onixary.sscPrimalstinct.instinct;

import com.google.gson.*;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.data.*;
import net.onixary.sscPrimalstinct.power.PowerPlanResolver;
import org.junit.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class OcelotStageDataTest {
    private JsonObject read(String path) throws Exception {
        try (var input = getClass().getResourceAsStream("/data/ssc-primalstinct/" + path + ".json")) {
            assertNotNull(path, input);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private Set<Identifier> ids(JsonArray array) {
        Set<Identifier> result = new HashSet<>();
        for (var item : array) result.add(new Identifier(item.getAsString()));
        return result;
    }
    @Test public void eachStageReplacesItsPredecessorAndCanBeRestoredOnDowngrade() throws Exception {
        var form = read("primalstinct/forms/ocelot_3");
        Map<Integer, PrimalLevels.LevelPowers> overrides = new HashMap<>();
        for (int level = 1; level <= 5; level++) {
            var powers = form.getAsJsonObject("level_overrides").getAsJsonObject("" + level).getAsJsonObject("powers");
            overrides.put(level, new PrimalLevels.LevelPowers(ids(powers.getAsJsonArray("add")), ids(powers.getAsJsonArray("remove"))));
            for (var id : ids(powers.getAsJsonArray("add"))) read("powers/" + id.getPath());
        }
        var profile = new PrimalFormProfile(new Identifier("shape-shifter-curse:ocelot_3"), true, 10, null,
                List.of(), List.of(), overrides, List.of(), null, null, "test");
        var levels = PrimalLevels.defaults();
        for (int stage : new int[]{1, 2, 3, 4, 5, 3, 1, 5}) {
            var plan = PowerPlanResolver.resolve(profile, stage, levels);
            assertEquals(overrides.get(stage).add, plan.grants.keySet());
            assertTrue(plan.grants.keySet().stream().allMatch(id -> id.getPath().contains("/level_" + stage + "/")));
        }
    }
    @Test public void schedulesAndInventoryMatchWhiteboard() throws Exception {
        int[] main = {18,18,12,9,0}, hotbar = {5,5,3,3,1};
        for (int level = 1; level <= 5; level++) {
            String path = "powers/ocelot_3/level_" + level + "/";
            assertEquals(main[level-1], read(path + "inventory").get("allowed_slots").getAsInt());
            assertEquals(hotbar[level-1], read(path + "hotbar").get("allowed_slots").getAsInt());
            assertEquals(level >= 4, read(path + "inventory").get("lock_equipment").getAsBoolean());
            if (level >= 3) assertEquals(new int[]{600,300,160}[level-3], read(path + "ai").get("afk_ticks").getAsInt());
            if (level >= 4) {
                var heat = read(path + "overheating");
                assertEquals(10, heat.get("check_interval").getAsInt());
                assertEquals(level == 4 ? 2.5 : 5.0, heat.get("growth_per_target").getAsDouble(), 0);
                assertEquals(level == 4 ? 2.5 : 5.0, heat.get("decay_per_second").getAsDouble(), 0);
                assertEquals(level == 4 ? 5 : 20, heat.get("duration_seconds").getAsInt());
            }
        }
    }
}
