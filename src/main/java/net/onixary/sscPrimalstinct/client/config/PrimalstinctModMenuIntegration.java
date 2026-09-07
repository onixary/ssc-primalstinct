package net.onixary.sscPrimalstinct.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** 卡17：ModMenu 入口（SSC ConfigIntegration 同款）——打开聚合配置菜单。 */
@Environment(EnvType.CLIENT)
public class PrimalstinctModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return PrimalstinctConfigMenuScreen::new;
    }
}
