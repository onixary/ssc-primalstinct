package net.onixary.sscPrimalstinct.items;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

import java.util.ArrayList;
import java.util.List;

/**
 * 卡14：物品注册 + 附属创造模式标签页。
 */
public final class RegPrimalstinctItems {

    private static final List<Item> GROUP_ITEMS = new ArrayList<>();

    /** 眷属实现03：终局方块物品在方块注册时追加进本组（entries 回调惰性读取，注册期追加即生效）。 */
    public static void appendEndgameItems(List<Item> items) {
        GROUP_ITEMS.addAll(items);
    }

    public static final Item SEDATIVE_FRAGMENT = register("sedative_fragment",
            new SedativeFragmentItem(new Item.Settings()));

    public static final Item PRIMAL_INSTINCT_FANG = register("primal_instinct_fang",
            new PrimalInstinctFangItem(new Item.Settings()));

    /** 眷属实现07：原初残余（创造取得/手动使用；生存常规途径为满级击杀自动触发的飞行表现）。 */
    public static final Item PRIMAL_REMNANT = register("primal_remnant",
            new PrimalRemnantItem(new Item.Settings()));

    public static final ItemGroup PRIMALSTINCT_GROUP = FabricItemGroup.builder()
            .icon(() -> new ItemStack(SEDATIVE_FRAGMENT))
            .displayName(Text.translatable("itemGroup.ssc-primalstinct"))
            .entries((context, entries) -> {
                for (Item item : GROUP_ITEMS) {
                    entries.add(item);
                }
            })
            .build();

    private RegPrimalstinctItems() {
    }

    private static Item register(String path, Item item) {
        Item registered = Registry.register(Registries.ITEM,
                Identifier.of(SSCPrimalstinct.MOD_ID, path), item);
        GROUP_ITEMS.add(registered);
        return registered;
    }

    public static void registerAll() {
        Registry.register(Registries.ITEM_GROUP,
                Identifier.of(SSCPrimalstinct.MOD_ID, "primalstinct"), PRIMALSTINCT_GROUP);
    }
}
