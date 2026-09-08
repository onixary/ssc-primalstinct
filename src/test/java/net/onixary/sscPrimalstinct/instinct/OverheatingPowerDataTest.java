package net.onixary.sscPrimalstinct.instinct;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

/** Protect the agreed periodic trigger and the link from the test form. */
public class OverheatingPowerDataTest {
    private JsonObject read(String resource) throws Exception {
        try (var input = getClass().getResourceAsStream(resource)) {
            assertNotNull(resource, input);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    public void checksPeriodicallyWithoutIndependentCooldown() throws Exception {
        var power = read("/data/ssc-primalstinct/powers/ocelot_3_instinct_overheating_test.json");
        assertEquals("apoli:action_over_time", power.get("type").getAsString());
        assertTrue(power.get("interval").getAsInt() > 0);
        assertFalse(power.toString().contains("cooldown"));
        var check = power.getAsJsonObject("entity_action");
        var conditions = check.getAsJsonObject("condition").getAsJsonArray("conditions");
        assertEquals("ssc-primalstinct:proxy_has_nearby_attack_target",
                conditions.get(1).getAsJsonObject().get("type").getAsString());
        double radius = conditions.get(1).getAsJsonObject().get("radius").getAsDouble();
        assertTrue(Double.isFinite(radius) && radius > 0 && radius <= 128);
        assertTrue(conditions.get(0).getAsJsonObject().get("inverted").getAsBoolean());
        var chance = check.getAsJsonObject("if_action");
        assertEquals("apoli:chance", chance.get("type").getAsString());
        double probability = chance.get("chance").getAsDouble();
        assertTrue(Double.isFinite(probability) && probability >= 0 && probability <= 1);
        var effect = chance.getAsJsonObject("action").getAsJsonObject("effect");
        assertEquals("ssc-primalstinct:instinct_overheating", effect.get("effect").getAsString());
        assertTrue(effect.get("duration").getAsInt() > 0);
    }

    @Test
    public void testPowerIsGrantedByOcelotForm() throws Exception {
        var form = read("/data/ssc-primalstinct/primalstinct/forms/ocelot_3.json");
        assertTrue(form.getAsJsonObject("base_powers").getAsJsonArray("add").toString()
                .contains("ssc-primalstinct:ocelot_3_instinct_overheating_test"));
    }
}
