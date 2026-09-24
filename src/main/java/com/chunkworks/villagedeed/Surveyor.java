/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed;

import com.chunkworks.villagedeed.api.Village;
import com.chunkworks.villagedeed.domain.Census;
import com.chunkworks.villagedeed.domain.Tariff;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.AABB;

/** Takes the census of a village from what stands and lives in its bounds: points of interest for
 * homes, workstations and bells; the residents and the golems, with a margin for the ones out in
 * the fields; blocks by each chunk section's palette, so a town costs a few hundred palette walks
 * and never a block loop; the contents of its containers. Loaded chunks only, which the buyer's
 * presence guarantees for the village they stand in. */
public final class Surveyor {
    /** How far outside the bounds residents and golems still count as the village's. */
    static final int RESIDENT_MARGIN = 8;
    private Surveyor() {}

    /** effects: the census of the village, counting only the blocks and items the tariff prices. */
    public static Census census(ServerLevel level, Village village, Tariff tariff) {
        var box = village.bounds();
        var census = Census.builder();
        census.footprint((box.getXSpan() * box.getZSpan() + 255) / 256);
        var centre = box.getCenter();
        int reach = (int) Math.ceil(Math.sqrt(sq(box.getXSpan() / 2.0) + sq(box.getYSpan() / 2.0) + sq(box.getZSpan() / 2.0))) + 2;
        level.getPoiManager().getInRange(type -> true, centre, reach, PoiManager.Occupancy.ANY).forEach(record -> {
            if (!box.isInside(record.getPos())) return;
            var type = record.getPoiType();
            if (type.is(PoiTypes.HOME)) census.bed();
            else if (type.is(PoiTypes.MEETING)) census.bell();
            else if (type.is(PoiTypeTags.ACQUIRABLE_JOB_SITE)) census.jobSite();
        });
        var area = AABB.of(box).inflate(RESIDENT_MARGIN);
        for (var villager : level.getEntitiesOfClass(Villager.class, area)) {
            if (villager.isBaby()) continue;
            var data = villager.getVillagerData();
            boolean employed = data.getProfession() != VillagerProfession.NONE && data.getProfession() != VillagerProfession.NITWIT;
            census.villager(employed ? Math.max(1, data.getLevel()) : 1);
        }
        for (var ignored : level.getEntitiesOfClass(IronGolem.class, area)) census.golem();
        var pricedBlocks = tariff.blocks().keySet();
        var pricedItems = tariff.items().keySet();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
            var chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            for (int sy = box.minY() >> 4; sy <= box.maxY() >> 4; sy++) {
                int index = chunk.getSectionIndexFromSectionY(sy);
                if (index < 0 || index >= chunk.getSectionsCount()) continue;
                var section = chunk.getSection(index);
                if (section.hasOnlyAir()) continue;
                section.getStates().count((state, n) -> {
                    var id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    if (pricedBlocks.contains(id)) census.block(id, n);
                });
            }
            for (var e : chunk.getBlockEntities().entrySet()) {
                if (!box.isInside(e.getKey()) || !(e.getValue() instanceof Container container)) continue;
                for (int i = 0; i < container.getContainerSize(); i++) {
                    var stack = container.getItem(i);
                    if (stack.isEmpty()) continue;
                    var id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    if (pricedItems.contains(id)) census.item(id, stack.getCount());
                }
            }
        }
        return census.build();
    }
    private static double sq(double x) { return x * x; }
}
