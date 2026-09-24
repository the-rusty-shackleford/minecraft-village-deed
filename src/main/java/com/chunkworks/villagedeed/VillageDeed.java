/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed;

import com.chunkworks.villagedeed.api.VillageProviders;
import com.chunkworks.villagedeed.village.StructureVillages;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Village Deed: buy a village and its law bends for you and the players you trust. A Thief
 * addon: the whole effect is one mixin on Thief's crime choke point ({@code CrimeMixin}), and
 * everything else is the shop front: the offer, the appraisal, the purchase, the claims and the
 * {@code /deed} command. Villages come through the {@link VillageProviders village protocol}. */
@Mod(VillageDeed.ID)
public final class VillageDeed {
    public static final String ID = "villagedeed";
    public static final Logger LOGGER = LoggerFactory.getLogger("Village Deed");

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }

    /** requires: the mod bus and container; effects: registers content, config, the structure
     * village provider and the event listeners. */
    public VillageDeed(IEventBus bus, ModContainer container) {
        ModItems.ITEMS.register(bus);
        bus.addListener(ModItems::onBuildCreativeTabs);
        container.registerConfig(ModConfig.Type.SERVER, DeedConfig.SPEC);
        VillageProviders.register(new StructureVillages());
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> DeedCommand.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(VillageDeed::onEntityInteract);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> DeedPurchase.forget(e.getEntity().getUUID()));
    }

    /** effects: sneak-use on an adult villager offers its village for sale. The event is not
     * cancelled: Thief's emerald gift is the same gesture and runs afterwards as before, and the
     * purchase waits for the click on the prompt, so nobody buys a village while trying to gift
     * an emerald. */
    static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getTarget() instanceof Villager villager) || villager.isBaby() || !player.isSecondaryUseActive()) return;
        DeedPurchase.offer(player, villager);
    }
}
