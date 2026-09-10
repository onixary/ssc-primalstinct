package net.onixary.sscPrimalstinct.endgame;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.onixary.sscPrimalstinct.endgame.worldgen.AvatarSanctum;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/** Read the actual shipped binary using Minecraft's NBT reader, independently of the Python writer. */
public class AvatarSanctumTemplateTest {
    private static NbtCompound root;
    private static NbtList palette;
    private static int[] cells;
    private static int sx, sy, sz;
    private static final Map<String, Integer> counts = new HashMap<>();

    @BeforeClass
    public static void loadBinary() throws Exception {
        try (var input = AvatarSanctumTemplateTest.class.getResourceAsStream(
                "/data/ssc-primalstinct/structures/avatar_sanctum.nbt")) {
            assertNotNull("Shipped sanctuary NBT", input);
            root = NbtIo.readCompressed(input);
        }
        var size = root.getList("size", 3);
        sx = size.getInt(0); sy = size.getInt(1); sz = size.getInt(2);
        cells = new int[sx * sy * sz];
        palette = root.getList("palette", 10);
        for (var element : root.getList("blocks", 10)) {
            var block = (NbtCompound) element;
            var pos = block.getList("pos", 3);
            int x = pos.getInt(0), y = pos.getInt(1), z = pos.getInt(2);
            assertTrue(x >= 0 && x < sx && y >= 0 && y < sy && z >= 0 && z < sz);
            int state = block.getInt("state");
            assertTrue(state >= 0 && state < palette.size());
            int index = (x * sy + y) * sz + z;
            assertEquals("Duplicate block coordinates", 0, cells[index]);
            cells[index] = state + 1;
            counts.merge(palette.getCompound(state).getString("Name"), 1, Integer::sum);
        }
    }

    private static String at(int x, int y, int z) {
        x -= AvatarSanctum.ORIGIN.getX();
        y -= AvatarSanctum.ORIGIN.getY();
        z -= AvatarSanctum.ORIGIN.getZ();
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return "minecraft:air";
        int value = cells[(x * sy + y) * sz + z];
        return value == 0 ? "minecraft:air" : palette.getCompound(value - 1).getString("Name");
    }

    @Test public void binaryMatchesRuntimeAnchorsAndContainsNoEntities() {
        assertEquals(3465, root.getInt("DataVersion"));
        assertEquals(AvatarSanctum.TEMPLATE_SIZE.getX(), sx);
        assertEquals(AvatarSanctum.TEMPLATE_SIZE.getY(), sy);
        assertEquals(AvatarSanctum.TEMPLATE_SIZE.getZ(), sz);
        assertEquals(0, root.getList("entities", 10).size());
        var conversion = AvatarSanctum.CONVERSION_PLATFORM;
        assertEquals("ssc-primalstinct:primal_conversion_platform", at(conversion.getX(), conversion.getY(), conversion.getZ()));
        assertEquals(Integer.valueOf(1), counts.get("ssc-primalstinct:primal_conversion_platform"));
        var gate = AvatarSanctum.RETURN_PORTAL_CENTER;
        assertEquals("ssc-primalstinct:primal_portal", at(gate.getX(), gate.getY(), gate.getZ()));
        assertEquals(Integer.valueOf(9), counts.get("ssc-primalstinct:primal_portal"));
        assertFalse(counts.containsKey("minecraft:water"));
        assertFalse(counts.containsKey("minecraft:lava"));
    }

    @Test public void arrivalAndThreeWideBridgeHaveFloorAndHeadroom() {
        var spawn = AvatarSanctum.SPAWN;
        assertNotEquals("minecraft:air", at(spawn.getX(), spawn.getY()-1, spawn.getZ()));
        for (int h = 0; h < 3; h++) assertEquals("minecraft:air", at(spawn.getX(), spawn.getY()+h, spawn.getZ()));
        for (int z = 0; z <= 49; z++) {
            int y = 95 + Math.min(12, Math.max(0, Math.floorDiv(z - 10, 2)));
            for (int x = -1; x <= 1; x++) {
                assertNotEquals("Bridge floor at " + x + "," + z, "minecraft:air", at(x,y,z));
                assertEquals("Bridge headroom", "minecraft:air", at(x,y+1,z));
                assertEquals("Bridge headroom", "minecraft:air", at(x,y+2,z));
            }
        }
    }

    @Test public void stoneLimbsLeaveAnimationClearanceAtTheirTips() {
        for (int[] tip : new int[][]{{6,117,44},{8,119,56},{12,114,64}}) {
            assertNotEquals("Stone attachment tip", "minecraft:air", at(tip[0],tip[1],tip[2]));
        }
        for (int x = 3; x <= 16; x++) for (int y = 125; y <= 144; y++) for (int z = 40; z <= 67; z++) {
            assertEquals("Animated tip space", "minecraft:air", at(x,y,z));
        }
    }
}
