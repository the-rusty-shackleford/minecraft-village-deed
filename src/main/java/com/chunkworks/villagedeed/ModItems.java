/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The deed item ({@link DeedItem}): a receipt stamped with the village, the price and who paid
 * it, which also opens its holder's trust list when used. */
public final class ModItems {
    private ModItems() {}
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VillageDeed.ID);
    public static final DeferredItem<DeedItem> VILLAGE_DEED = ITEMS.register("village_deed", () -> new DeedItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(VILLAGE_DEED);
    }
}
