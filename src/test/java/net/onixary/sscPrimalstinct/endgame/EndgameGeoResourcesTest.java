package net.onixary.sscPrimalstinct.endgame;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.endgame.client.EndgameBlockVisual;
import org.junit.Test;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;
import software.bernie.geckolib.util.JsonUtil;

import static org.junit.Assert.*;

/** Exercise the shipped assets with GeckoLib's own deserializer and model baker. */
public class EndgameGeoResourcesTest {
    private InputStream resource(Identifier id) {
        var stream = getClass().getResourceAsStream("/assets/" + id.getNamespace() + "/" + id.getPath());
        assertNotNull("Missing resource: " + id, stream);
        return stream;
    }

    private JsonObject json(Identifier id) throws Exception {
        try (var reader = new InputStreamReader(resource(id), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private BufferedImage image(Identifier id) throws Exception {
        try (var stream = resource(id)) {
            var image = ImageIO.read(stream);
            assertNotNull("Invalid PNG: " + id, image);
            return image;
        }
    }

    @Test
    public void everyStateLoadsAndBakesWithGeckoLib4() throws Exception {
        assertEquals(10, EndgameBlockVisual.values().length);
        for (var visual : EndgameBlockVisual.values()) {
            var raw = json(visual.model);
            Model parsed = JsonUtil.GEO_GSON.fromJson(raw, Model.class);
            assertNotNull("Supported geo version: " + visual, parsed.formatVersion());
            assertEquals(1, parsed.minecraftGeometry().length);
            var baked = BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(parsed));
            assertFalse("Renderable bones: " + visual, baked.topLevelBones().isEmpty());

            var texture = image(visual.texture);
            var properties = parsed.minecraftGeometry()[0].modelProperties();
            assertEquals(texture.getWidth(), properties.textureWidth(), 0);
            assertEquals(texture.getHeight(), properties.textureHeight(), 0);
            assertEquals(32, texture.getWidth());
            assertEquals(32, texture.getHeight());

            var geometry = raw.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            // 转化台的符文阵半径约 1.5 格（02_Rune_circle 未旋转包围盒到 ±23.8），单独放宽到 ±24；
            // 其余状态连同旋转立方体的未旋转包围盒都仍须落在一格以内。
            double half = visual == EndgameBlockVisual.CONVERSION_PLATFORM ? 24 : 8;
            var names = new HashSet<String>();
            int cubes = 0;
            for (var element : geometry.getAsJsonArray("bones")) {
                var bone = element.getAsJsonObject();
                assertTrue("Unique bone: " + visual, names.add(bone.get("name").getAsString()));
                if (!bone.has("cubes")) continue;
                for (var value : bone.getAsJsonArray("cubes")) {
                    cubes++;
                    var cube = value.getAsJsonObject();
                    for (int axis = 0; axis < 3; axis++) {
                        double size = cube.getAsJsonArray("size").get(axis).getAsDouble();
                        double start = cube.getAsJsonArray("origin").get(axis).getAsDouble();
                        assertTrue("Minimum cube axis: " + visual, size >= 0.5);
                        assertTrue("Centred block bounds: " + visual, start >= (axis == 1 ? 0 : -half));
                        assertTrue("Centred block bounds: " + visual, start + size <= (axis == 1 ? 16 : half));
                    }
                    for (var face : cube.getAsJsonObject("uv").entrySet()) {
                        var uv = face.getValue().getAsJsonObject();
                        for (int axis = 0; axis < 2; axis++) {
                            double start = uv.getAsJsonArray("uv").get(axis).getAsDouble();
                            double end = start + uv.getAsJsonArray("uv_size").get(axis).getAsDouble();
                            assertTrue("UV within texture: " + visual, start >= 0 && start <= 32 && end >= 0 && end <= 32);
                        }
                    }
                }
            }
            assertEquals(expectedCubeCount(visual), cubes);
            assertNotNull(JsonUtil.GEO_GSON.fromJson(json(visual.animation).get("animations"), BakedAnimations.class));
        }
    }

    /** 已经换成正式几何体的状态各有固定立方体数，其余仍是单立方体占位。 */
    private int expectedCubeCount(EndgameBlockVisual visual) {
        return switch (visual) {
            case PEDESTAL, PEDESTAL_FULFILLED -> 22;
            case ALTAR, ALTAR_ACTIVE -> 13;
            case SPENT_ALTAR -> 20;
            case CONVERSION_PLATFORM -> 18;
            case PORTAL_FRAME -> 7;
            case WIRE, WIRE_LIT -> 6;
            default -> 1;
        };
    }

    /**
     * 祭坛三个状态的每个面都按 1 单位 = 1 像素铺 UV。
     * 一旦 UV 尺寸和面的模型尺寸对不上，贴图就会被拉伸。
     */
    @Test
    public void altarFacesMapOneTexelPerUnit() throws Exception {
        for (var visual : new EndgameBlockVisual[] {
                EndgameBlockVisual.ALTAR, EndgameBlockVisual.ALTAR_ACTIVE, EndgameBlockVisual.SPENT_ALTAR}) {
            var geometry = json(visual.model).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            for (var element : geometry.getAsJsonArray("bones")) {
                var bone = element.getAsJsonObject();
                if (!bone.has("cubes")) continue;
                for (var value : bone.getAsJsonArray("cubes")) {
                    var cube = value.getAsJsonObject();
                    var size = cube.getAsJsonArray("size");
                    for (var face : cube.getAsJsonObject("uv").entrySet()) {
                        int[] axes = switch (face.getKey()) {
                            case "north", "south" -> new int[] {0, 1};
                            case "east", "west" -> new int[] {2, 1};
                            default -> new int[] {0, 2};
                        };
                        var uv = face.getValue().getAsJsonObject();
                        for (int axis = 0; axis < 2; axis++) {
                            double texels = Math.abs(uv.getAsJsonArray("uv_size").get(axis).getAsDouble());
                            assertEquals("UV texels match face size: " + visual,
                                    size.get(axes[axis]).getAsDouble(), texels, 1e-6);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void stateChangesPreserveGeometryAndUv() throws Exception {
        for (var pair : new EndgameBlockVisual[][] {
                {EndgameBlockVisual.PEDESTAL, EndgameBlockVisual.PEDESTAL_FULFILLED},
                {EndgameBlockVisual.ALTAR, EndgameBlockVisual.ALTAR_ACTIVE},
                {EndgameBlockVisual.WIRE, EndgameBlockVisual.WIRE_LIT}}) {
            var first = json(pair[0].model).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            var second = json(pair[1].model).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            assertEquals(canonicalBones(first), canonicalBones(second));
            assertNotEquals(pair[0].texture, pair[1].texture);
        }
    }

    /**
     * geo 里 bones 和 cubes 的顺序对渲染没有影响——Blockbench 重新导出时常会把它们重排，
     * 同一份几何因此变成"不相等"。这里按内容排序后再比，只关心几何本身相不相同。
     */
    private List<String> canonicalBones(JsonObject geometry) {
        var bones = new ArrayList<String>();
        for (var element : geometry.getAsJsonArray("bones")) {
            var bone = element.getAsJsonObject();
            var cubes = new ArrayList<String>();
            if (bone.has("cubes")) {
                for (var cube : bone.getAsJsonArray("cubes")) cubes.add(cube.toString());
                Collections.sort(cubes);
            }
            bones.add(bone.get("name").getAsString() + "|" + bone.get("pivot") + "|" + cubes);
        }
        Collections.sort(bones);
        return bones;
    }

    @Test
    public void emissionMasksLightOnlyPartOfTheirModel() throws Exception {
        for (var visual : new EndgameBlockVisual[] {
                EndgameBlockVisual.PEDESTAL_FULFILLED, EndgameBlockVisual.ALTAR_ACTIVE,
                EndgameBlockVisual.CONVERSION_PLATFORM}) {
            var mask = image(visual.emission);
            var base = image(visual.texture);
            assertEquals("Mask size: " + visual, base.getWidth(), mask.getWidth());
            assertEquals("Mask size: " + visual, base.getHeight(), mask.getHeight());
            int emitting = 0;
            for (int y = 0; y < mask.getHeight(); y++) {
                for (int x = 0; x < mask.getWidth(); x++) {
                    if ((mask.getRGB(x, y) >>> 24) != 0) emitting++;
                }
            }
            assertTrue("Mask has luminous pixels: " + visual, emitting > 0);
            assertTrue("Stone and moss remain non-emissive: " + visual,
                    emitting < mask.getWidth() * mask.getHeight() / 2);
        }
    }

    @Test
    public void allSevenBlockItemsUseCustomRenderingAndKeepDisplayTransforms() throws Exception {
        for (String name : new String[] {"primal_pedestal", "primal_altar", "primal_energy_wire",
                "spent_primal_altar", "primal_portal_frame", "primal_portal", "primal_conversion_platform"}) {
            var item = json(EndgameRules.id("models/item/" + name + ".json"));
            assertEquals("builtin/entity", item.get("parent").getAsString());
            assertTrue(item.getAsJsonObject("display").has("gui"));
            assertTrue(item.getAsJsonObject("display").has("ground"));
            assertTrue(item.getAsJsonObject("display").has("firstperson_righthand"));
            image(new Identifier(item.getAsJsonObject("textures").get("particle").getAsString()
                    .replace(":", ":textures/") + ".png"));
        }
    }
}
