package net.onixary.sscPrimalstinct.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.network.CurlSleepC2S;
import org.lwjgl.glfw.GLFW;

public final class CurlSleepKeybinding {

    private static KeyBinding curlSleep;

    private CurlSleepKeybinding() {
    }

    public static void register() {
        curlSleep = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ssc-primalstinct.curl_sleep",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.ssc-primalstinct"
        ));
    }

    public static void tick() {
        if (curlSleep != null && curlSleep.wasPressed()) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    CurlSleepC2S.ID,
                    new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer()));
            SSCPrimalstinct.LOGGER.debug("[primalstinct] curl sleep key pressed");
        }
    }
}
