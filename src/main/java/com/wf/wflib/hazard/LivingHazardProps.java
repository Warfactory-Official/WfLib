package com.wf.wflib.hazard;

import com.wf.wflib.WFLib;
import com.wf.wflib.radiation.RadiationConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Per-entity hazard state; not copied on death.
public final class LivingHazardProps {

    /**
     * Accumulated-radiation ceiling: 2500 is what both 1.12.2 forks enforce, their setter clamping right after their increment.
     */
    public static final double RAD_CAP = 2500D;
    public static final int maxAsbestos = 60 * 1200;
    public static final int maxBlacklung = 120 * 1200;
    public static final MapCodec<LivingHazardProps> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("radiation", 0D).forGetter(p -> p.radiation),
            Codec.DOUBLE.optionalFieldOf("digamma", 0D).forGetter(p -> p.digamma),
            Codec.INT.optionalFieldOf("asbestos", 0).forGetter(p -> p.asbestos),
            Codec.INT.optionalFieldOf("blacklung", 0).forGetter(p -> p.blacklung),
            ContaminationEffect.CODEC.listOf().optionalFieldOf("contamination", List.of())
                                     .forGetter(LivingHazardProps::getCont)
    ).apply(i, LivingHazardProps::create));
    /**
     * Id of the (0.5^digamma - 1) ADD_MULTIPLIED_TOTAL max-health modifier (1.7 digamma_UUID).
     */
    private static final ResourceLocation DIGAMMA_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "digamma_health");
    /**
     * Null until something contaminates: an eager list would allocate on almost every entity that ever ticks.
     */
    private @Nullable List<ContaminationEffect> contamination;
    public double radiation;
    public double digamma;
    public int asbestos;
    public int blacklung;
    public double radEnv;
    public double radBuf;
    public double neutron;

    public LivingHazardProps() {
    }

    private static LivingHazardProps create(double radiation, double digamma, int asbestos, int blacklung,
                                            List<ContaminationEffect> contamination) {
        LivingHazardProps p = new LivingHazardProps();
        p.radiation = radiation;
        p.digamma = digamma;
        p.asbestos = asbestos;
        p.blacklung = blacklung;
        if (!contamination.isEmpty()) p.contamination = new ArrayList<>(contamination);
        return p;
    }

    public static LivingHazardProps getData(LivingEntity entity) {
        return entity.getData(HazardContent.LIVING_PROPS);
    }

    /**
     * Create-on-miss would give every living entity a permanent all-default record in its save data.
     */
    public static @Nullable LivingHazardProps peek(LivingEntity entity) {
        return entity.hasData(HazardContent.LIVING_PROPS) ? entity.getData(HazardContent.LIVING_PROPS) : null;
    }

    /** True while every field still holds its default, which is the state worth not persisting. */
    public boolean isDefault() {
        return radiation == 0D && digamma == 0D && asbestos == 0 && blacklung == 0
                && radEnv == 0D && radBuf == 0D && neutron == 0D
                && (contamination == null || contamination.isEmpty());
    }

    /**
     * The gates live HERE rather than on the statics so that neither route can bypass them.
     */
    public double radiation() {
        return RadiationConfig.enableContamination ? radiation : 0D;
    }

    public int asbestos() {
        return RadiationConfig.disableAsbestos ? 0 : asbestos;
    }

    public int blackLung() {
        return RadiationConfig.disableCoal ? 0 : blacklung;
    }

    public List<ContaminationEffect> getCont() {
        return contamination == null ? List.of() : contamination;
    }

    public void addCont(ContaminationEffect cont) {
        if (contamination == null) contamination = new ArrayList<>(2);
        contamination.add(cont);
    }

    public void setCont(ContaminationEffect[] cont) {
        if (cont.length == 0) {
            if (contamination != null) contamination.clear();
            return;
        }
        if (contamination == null) contamination = new ArrayList<>(cont.length);
        else contamination.clear();
        Collections.addAll(contamination, cont);
    }

    public static double getRadiation(LivingEntity entity) {
        return getData(entity).radiation();
    }

    public static void setRadiation(LivingEntity entity, double rad) {
        if (RadiationConfig.enableContamination) getData(entity).radiation = rad;
    }

    public static void incrementRadiation(LivingEntity entity, double rad) {
        if (!RadiationConfig.enableContamination) return;
        LivingHazardProps p = getData(entity);
        p.radiation = Mth.clamp(p.radiation + rad, 0D, RAD_CAP);
    }

    public static double getRadEnv(LivingEntity entity) {
        return getData(entity).radEnv;
    }

    public static void setRadEnv(LivingEntity entity, double rad) {
        getData(entity).radEnv = rad;
    }

    public static double getRadBuf(LivingEntity entity) {
        return getData(entity).radBuf;
    }

    public static void setRadBuf(LivingEntity entity, double rad) {
        getData(entity).radBuf = rad;
    }

    public static double getNeutron(LivingEntity entity) {
        return getData(entity).neutron;
    }

    public static void setNeutron(LivingEntity entity, double rad) {
        getData(entity).neutron = Math.max(rad, 0D);
    }

    public static double getDigamma(LivingEntity entity) {
        return getData(entity).digamma;
    }

    // 1.7: (0.5^digamma - 1) max-health; 10 = kill.
    public static void setDigamma(LivingEntity entity, double digamma) {
        digamma = Mth.clamp(digamma, 0D, 1000D);
        getData(entity).digamma = digamma;

        AttributeInstance attr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;

        double healthMod = Math.pow(0.5D, digamma) - 1D;
        attr.addOrUpdateTransientModifier(
                new AttributeModifier(DIGAMMA_MODIFIER_ID, healthMod,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

        if (entity.getHealth() > entity.getMaxHealth() && entity.getMaxHealth() > 0F) {
            entity.setHealth(entity.getMaxHealth());
        }
        if ((entity.getMaxHealth() <= 0F || digamma >= 10D) && entity.isAlive()
                && entity.level() instanceof ServerLevel) {
            entity.setAbsorptionAmount(0F);
            HazardContent.hurt(entity, HazardContent.DAMAGE_DIGAMMA, 500F);
            entity.setHealth(0F);
        }
        // TODO(ntm-port): HbmCriteria digamma/radiation advancements not ported.
    }

    public static void incrementDigamma(LivingEntity entity, double digamma) {
        setDigamma(entity, getDigamma(entity) + digamma);
    }

    public static int getAsbestos(LivingEntity entity) {
        return getData(entity).asbestos();
    }

    public static void setAsbestos(LivingEntity entity, int asbestos) {
        if (RadiationConfig.disableAsbestos) return;
        LivingHazardProps p = getData(entity);
        p.asbestos = asbestos;
        if (asbestos >= maxAsbestos) {
            p.asbestos = 0;
            HazardContent.hurt(entity, HazardContent.DAMAGE_ASBESTOS, 1000F);
        }
    }

    public static void incrementAsbestos(LivingEntity entity, int asbestos) {
        if (RadiationConfig.disableAsbestos) return;
        setAsbestos(entity, getAsbestos(entity) + asbestos);
        informGasHazard(entity, "info.asbestos");
    }

    private static void informGasHazard(LivingEntity entity, String key) {
        // Divergence: NTM keyed HUD toast -> action bar.
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
        }
    }

    public static int getBlackLung(LivingEntity entity) {
        return getData(entity).blackLung();
    }

    public static void setBlackLung(LivingEntity entity, int bl) {
        if (RadiationConfig.disableCoal) return;
        LivingHazardProps p = getData(entity);
        p.blacklung = bl;
        if (bl >= maxBlacklung) {
            p.blacklung = 0;
            HazardContent.hurt(entity, HazardContent.DAMAGE_BLACKLUNG, 1000F);
        }
    }

    public static void incrementBlackLung(LivingEntity entity, int bl) {
        if (RadiationConfig.disableCoal) return;
        setBlackLung(entity, getBlackLung(entity) + bl);
        informGasHazard(entity, "info.coaldust");
    }

    public static List<ContaminationEffect> getCont(LivingEntity entity) {
        return getData(entity).getCont();
    }

    public static void addCont(LivingEntity entity, ContaminationEffect cont) {
        getData(entity).addCont(cont);
    }
}
