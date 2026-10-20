package com.wf.wflib.barrel;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** {@code wflib:spare_barrel}: one quick-change barrel; type + heat + wear in {@link Barrels#SPARE}. */
public class SpareBarrelItem extends Item {
    public SpareBarrelItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public Component getName(ItemStack stack) {
        SpareBarrel b = stack.get(Barrels.SPARE.get());
        return b == null ? super.getName(stack)
                : Component.translatable("item.wflib.spare_barrel.typed", BarrelTypes.name(b.type()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        SpareBarrel b = stack.get(Barrels.SPARE.get());
        if (b == null) {
            return;
        }
        Barrels.tooltip(BarrelTypes.spec(b.type()), b.state(), tooltip);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        SpareBarrel b = stack.get(Barrels.SPARE.get());
        return b != null && b.state().wear() > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        SpareBarrel b = stack.get(Barrels.SPARE.get());
        return b == null ? 13 : Math.round(13 * (1 - b.state().wear()));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        SpareBarrel b = stack.get(Barrels.SPARE.get());
        return Mth.hsvToRgb((b == null ? 1 : 1 - b.state().wear()) / 3, 1, 1);
    }
}
