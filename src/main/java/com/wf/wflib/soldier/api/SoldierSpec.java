package com.wf.wflib.soldier.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.List;
import java.util.Map;

/**
 * Who to spawn and how they are kitted.
 *
 * @param team a side's name: soldiers of different teams fight
 * @param namePrefix profile names are {@code <prefix>_<n>}
 * @param huntsPlayers whether real players count as this team's enemies (false: they count as friends)
 * @param gun the gun issued
 * @param grenades issued into the hotbar, in order
 * @param armour armour issued, by slot
 * @param spares spare magazines
 */
public record SoldierSpec(String team, String namePrefix, boolean huntsPlayers, ResourceLocation gun,
                          List<ResourceLocation> grenades, Map<EquipmentSlot, ResourceLocation> armour, int spares) {
}
