package net.onixary.sscPrimalstinct.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 卡15：本能条客户端配置（config/ssc-primalstinct-client.json，Gson 明文）。
 * 锚点 1–9 与 SSC UIPositionUtils 的九宫格一致；默认值对齐 SSC 旧本能条默认位
 * （posType=8 中下锚 + (100, -9)），保持“原条位置”的迁移体验。
 * 用户 offset 配置用于与法力/蛛丝/护甲/饥饿条共存时手工避让（法力条在场时另有 -14 自动上移）。
 */
public final class PrimalstinctClientConfig {

    /** 九宫格锚点（1=左上 … 5=中心 … 9=右下），同 SSC UIPositionUtils。 */
    public int barPosType = 8;
    public int barOffsetX = 100;
    public int barOffsetY = -9;
    /** 是否在条右侧绘制“锁定/速率暂停”状态文本。 */
    public boolean showStatusText = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static PrimalstinctClientConfig instance;

    public static PrimalstinctClientConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("ssc-primalstinct-client.json");
    }

    private static PrimalstinctClientConfig load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try {
                PrimalstinctClientConfig config = GSON.fromJson(Files.readString(path), PrimalstinctClientConfig.class);
                if (config != null) {
                    config.clampValues();
                    return config;
                }
            } catch (Exception e) {
                SSCPrimalstinct.LOGGER.warn("[primalstinct] 客户端配置解析失败，使用默认值：{}", e.toString());
            }
        }
        PrimalstinctClientConfig config = new PrimalstinctClientConfig();
        config.save();
        return config;
    }

    public void save() {
        clampValues();
        try {
            Path path = configPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this));
        } catch (IOException e) {
            SSCPrimalstinct.LOGGER.warn("[primalstinct] 客户端配置写入失败：{}", e.toString());
        }
    }

    private void clampValues() {
        if (barPosType < 1 || barPosType > 9) {
            barPosType = 8;
        }
        if (barOffsetX < -2000 || barOffsetX > 2000) {
            barOffsetX = 100;
        }
        if (barOffsetY < -2000 || barOffsetY > 2000) {
            barOffsetY = -9;
        }
    }
}
