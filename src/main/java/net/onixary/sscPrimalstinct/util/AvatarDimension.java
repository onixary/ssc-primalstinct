package net.onixary.sscPrimalstinct.util;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

/**
 * 眷属实现09：原始化身维度的注册键与访问入口。
 * 维度本体由数据包 dimension/primal_avatar.json 声明（1.20.1 数据驱动维度，
 * 新维度对既有世界自动并入）；此处在代码侧提供统一的键与获取方式。
 */
public final class AvatarDimension {

    public static final RegistryKey<World> WORLD_KEY =
            RegistryKey.of(RegistryKeys.WORLD, Identifier.of(SSCPrimalstinct.MOD_ID, "primal_avatar"));

    private AvatarDimension() {
    }

    /** @return 维度实例；未注册（数据包缺失/被移除）时为 null。 */
    public static ServerWorld world(MinecraftServer server) {
        return server.getWorld(WORLD_KEY);
    }
}
