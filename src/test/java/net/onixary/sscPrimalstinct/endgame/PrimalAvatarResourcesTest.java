package net.onixary.sscPrimalstinct.endgame;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import org.junit.Test;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.util.JsonUtil;

import static org.junit.Assert.*;

public class PrimalAvatarResourcesTest {
    private JsonObject json(String path) throws Exception {
        var stream = getClass().getResourceAsStream("/assets/ssc-primalstinct/" + path);
        assertNotNull(path, stream);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    public void animationsTargetTheShippedSkeleton() throws Exception {
        var geometry = json("geo/entity/primal_avatar.geo.json")
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        var names = new HashSet<String>();
        for (var bone : geometry.getAsJsonArray("bones")) {
            names.add(bone.getAsJsonObject().get("name").getAsString());
        }
        var animations = json("animations/primal_avatar.animation.json").getAsJsonObject("animations");
        assertNotNull(JsonUtil.GEO_GSON.fromJson(animations, BakedAnimations.class));
        for (String name : new String[]{"idle", "interact"}) {
            var animation = animations.getAsJsonObject("animation.primal_avatar." + name);
            assertNotNull(name, animation);
            var tracks = animation.getAsJsonObject("bones");
            assertTrue("Animation must contain bone tracks", tracks.size() > 0);
            for (var track : tracks.entrySet()) {
                assertTrue("Unknown animation bone: " + track.getKey(), names.contains(track.getKey()));
                assertFalse("No scale animation", track.getValue().getAsJsonObject().has("scale"));
            }
        }
        assertTrue(animations.getAsJsonObject("animation.primal_avatar.idle").get("loop").getAsBoolean());
        assertEquals(5.0, animations.getAsJsonObject("animation.primal_avatar.interact")
                .get("animation_length").getAsDouble(), 0.0);
    }
}
