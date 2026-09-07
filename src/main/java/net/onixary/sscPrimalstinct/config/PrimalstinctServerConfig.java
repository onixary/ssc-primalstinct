package net.onixary.sscPrimalstinct.config;

import com.google.gson.annotations.SerializedName;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

/**
 * 卡17：服务端/世界级开局选择配置（config/ssc-primalstinct-server.json，SSC 同款 AutoConfig + ModMenu）。
 * 语义（卡17）：开关只决定首次进入路线；会话中的值以服务端启动时的快照为准——
 * 经 ModMenu 修改只写入文件，重启服务器/重进世界后生效，避免运行中切换造成 pending 边界态。
 * 联机时由服务器本地文件权威决定（客户端 ModMenu 改的是自己本地文件，不下发）。
 */
@Config(name = "ssc-primalstinct-server")
public class PrimalstinctServerConfig implements me.shedaniel.autoconfig.ConfigData {

    @ConfigEntry.Category("General")
    @Comment("Choose form on start: open the form selection screen on first entry. false = SSC normal route (book start, legacy instinct, feral progression). Restart / world re-enter to apply. Default: false")
    @SerializedName("choose_form_on_start")
    public boolean chooseFormOnStart = false;

    /** 本会话权威值（服务端启动时从 AutoConfig holder 快照）。 */
    private static PrimalstinctServerConfig active = new PrimalstinctServerConfig();

    public static boolean chooseFormOnStart() {
        return active.chooseFormOnStart;
    }

    /** mod 初始化（双端）注册并读取文件。 */
    public static void register() {
        AutoConfig.register(PrimalstinctServerConfig.class, GsonConfigSerializer::new);
    }

    /** SERVER_STARTING 时快照：本次会话固定使用该值（卡17“重启生效”语义）。 */
    public static void snapshotActive() {
        active = AutoConfig.getConfigHolder(PrimalstinctServerConfig.class).getConfig();
        SSCPrimalstinct.LOGGER.info("[primalstinct] choose_form_on_start={}", active.chooseFormOnStart);
    }
}
