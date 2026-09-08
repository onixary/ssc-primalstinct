package net.onixary.sscPrimalstinct.instinct;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

/** Protect the meter trigger semantics and the link from the test form. */
public class OverheatingPowerDataTest {
    private JsonObject read(String resource) throws Exception {
        try (var input = getClass().getResourceAsStream(resource)) {
            assertNotNull(resource, input);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    public void meterGrowsPerTargetAndDecaysWithoutTargets() throws Exception {
        var power = read("/data/ssc-primalstinct/powers/ocelot_3/level_4/overheating.json");
        assertEquals("ssc-primalstinct:instinct_overheat", power.get("type").getAsString());
        assertTrue(power.toString().contains("growth_per_target"));
        assertTrue(power.toString().contains("decay_per_second"));
        double radius = power.get("radius").getAsDouble();
        assertTrue(Double.isFinite(radius) && radius > 0 && radius <= 128);
        int interval = power.get("check_interval").getAsInt();
        assertTrue(interval >= 1);
        double growth = power.get("growth_per_target").getAsDouble();
        double decay = power.get("decay_per_second").getAsDouble();
        assertTrue(Double.isFinite(growth) && growth > 0);
        assertTrue(Double.isFinite(decay) && decay >= 0);
        // 增速封顶必须不小于单目标增速，否则单目标也被削
        double cap = power.get("max_growth_per_second").getAsDouble();
        assertTrue(Double.isFinite(cap) && cap >= growth);
        assertTrue(power.get("duration_seconds").getAsInt() > 0);
    }

    @Test
    public void higherLevelFillsFasterWithLongerBuff() throws Exception {
        var low = read("/data/ssc-primalstinct/powers/ocelot_3/level_4/overheating.json");
        var high = read("/data/ssc-primalstinct/powers/ocelot_3/level_5/overheating.json");
        assertTrue(high.get("growth_per_target").getAsDouble() > low.get("growth_per_target").getAsDouble());
        assertTrue(high.get("duration_seconds").getAsInt() > low.get("duration_seconds").getAsInt());
    }

    @Test
    public void productionPowersAreGrantedAtTheirLevels() throws Exception {
        var form = read("/data/ssc-primalstinct/primalstinct/forms/ocelot_3.json");
        assertTrue(form.getAsJsonObject("level_overrides").getAsJsonObject("4").getAsJsonObject("powers")
                .getAsJsonArray("add").toString().contains("ssc-primalstinct:ocelot_3/level_4/overheating"));
    }
}
