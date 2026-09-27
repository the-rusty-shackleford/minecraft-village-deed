/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.client;

import com.chunkworks.villagedeed.TrustList;
import com.chunkworks.villagedeed.VillageDeed;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** The client's registrations: the trust screen takes the listings the server sends. */
@EventBusSubscriber(modid = VillageDeed.ID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) { TrustList.Client.receiver(TrustScreen::accept); }
}
