package com.wf.wflib.hazard.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.wf.wflib.hazard.transformer.HazardTransformerPostCustom;
import com.wf.wflib.hazard.transformer.HazardTransformerRadiationContainer;
import com.wf.wflib.hazard.transformer.HazardTransformerRadiationNBT;
import com.wf.wflib.hazard.type.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import com.wf.wflib.core.api.WFLib;


// IHazardType singletons: identity-by-reference. Divergence: no item rows (NTM content); packs ship
// wflib:hazard assignments, and code registers modifier-carrying rows via HazardSystem.register.
public final class Hazards {

    public static final IHazardType RADIATION = new HazardTypeRadiation();
    public static final IHazardType CONTAMINATING = new HazardTypeContaminating();
    public static final IHazardType DIGAMMA = new HazardTypeDigamma();
    public static final IHazardType HOT = new HazardTypeHot();
    public static final IHazardType BLINDING = new HazardTypeBlinding();
    public static final IHazardType ASBESTOS = new HazardTypeAsbestos();
    public static final IHazardType COAL = new HazardTypeCoal();
    public static final IHazardType HYDROACTIVE = new HazardTypeHydroactive();
    public static final IHazardType EXPLOSIVE = new HazardTypeExplosive();
    public static final IHazardType TOXIC = new HazardTypeToxic();
    public static final IHazardType COLD = new HazardTypeCold();

    /**
     * The datapack spelling of each hazard type. Identity-by-reference singletons need a name to cross the
     * codec boundary, and the name is the ONE place it is written down.
     */
    private static final Map<ResourceLocation, IHazardType> TYPES_BY_ID = new LinkedHashMap<>();
    private static final Map<IHazardType, ResourceLocation> IDS_BY_TYPE = new IdentityHashMap<>();

    static {
        nameType("radiation", RADIATION);
        nameType("contaminating", CONTAMINATING);
        nameType("digamma", DIGAMMA);
        nameType("hot", HOT);
        nameType("blinding", BLINDING);
        nameType("asbestos", ASBESTOS);
        nameType("coal", COAL);
        nameType("hydroactive", HYDROACTIVE);
        nameType("explosive", EXPLOSIVE);
        nameType("toxic", TOXIC);
        nameType("cold", COLD);
    }

    public static final Codec<IHazardType> TYPE_CODEC = ResourceLocation.CODEC.comapFlatMap(
            id -> {
                IHazardType type = TYPES_BY_ID.get(id);
                return type == null
                        ? DataResult.error(() -> "unknown hazard type " + id + ", known: " + TYPES_BY_ID.keySet())
                        : DataResult.success(type);
            },
            Hazards::idOf);

    private static void nameType(String name, IHazardType type) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, name);
        TYPES_BY_ID.put(id, type);
        IDS_BY_TYPE.put(type, id);
    }

    public static ResourceLocation idOf(IHazardType type) {
        ResourceLocation id = IDS_BY_TYPE.get(type);
        if (id == null) throw new IllegalArgumentException("hazard type carries no datapack name: " + type);
        return id;
    }
    // 1.7: CRYSTAL = gem, not block.
    public static final double mult_nugget = 0.1D;
    public static final double mult_ingot = 1.0D;
    public static final double mult_gem = 1.0D;
    public static final double mult_plate = mult_ingot;
    public static final double mult_plateCast = mult_plate * 3D;
    public static final double mult_powder = mult_ingot * 3D;
    public static final double mult_powderTiny = mult_nugget * 3D;
    public static final double mult_ore = mult_ingot;
    public static final double mult_billet = 0.5D;
    public static final double mult_block = 10.0D;
    /**
     * 1.7 rod/rod_rbmk/rtg shape factors: an RBMK rod holds eight breeding rods, an RTG pellet three billets.
     */
    public static final double mult_rod = 0.5D;
    public static final double mult_rod_rbmk = mult_rod * 8D;
    public static final double mult_rtg = mult_billet * 3D;
    public static final double mult_crystal = mult_gem;
    // 1.7: rad/s at ingot multiplier (1.7 HazardRegistry.java:73-124).
    public static final double co60 = 30.0D;
    public static final double tc99 = 2.75D;
    public static final double au198 = 500.0D;
    public static final double pb209 = 10000.0D;
    public static final double po210 = 75.0D;
    public static final double ra226 = 7.5D;
    public static final double ac227 = 30.0D;
    public static final double th232 = 0.1D;
    public static final double u = 0.35D;
    public static final double u233 = 5.0D;
    public static final double u235 = 1.0D;
    public static final double u238 = 0.25D;
    public static final double np237 = 2.5D;
    public static final double pu = 7.5D;
    public static final double purg = 6.25D;
    public static final double pu238 = 10.0D;
    public static final double pu239 = 5.0D;
    public static final double pu240 = 7.5D;
    public static final double pu241 = 25.0D;
    public static final double am241 = 8.5D;
    public static final double am242 = 9.5D;
    public static final double amrg = 9.0D;
    // 1.7: reactor-fuel alloy doses (91,109).
    public static final double uf = 0.5D;
    public static final double saf = 5.85D;
    // 1.7: remaining fuel-alloy and block doses (86..126).
    public static final double thf = 1.75D;
    public static final double uzh = 0.125D;
    public static final double npf = 1.5D;
    public static final double puf = 4.25D;
    public static final double amf = 4.75D;
    public static final double mox = 2.5D;
    public static final double trn = 0.1D;
    public static final double trx = 25.0D;
    public static final double sa326 = 15.0D;
    public static final double sa327 = 17.5D;
    public static final double gh336 = 5.0D;
    public static final double mud = 1.0D;
    public static final double sr = sa326 * 0.1D;
    public static final double sb = sa326 * 0.1D;
    public static final double sas3 = 5.0D;
    // 1.7: (a,Be) at triple dose (113-115).
    public static final double sr90 = 15.0D;
    public static final double i131 = 150.0D;
    public static final double xe135 = 1250.0D;
    public static final double cs137 = 20.0D;
    public static final double at209 = 7500.0D;
    public static final double bf = 300_000.0D;
    public static final double radsource_mult = 3.0D;
    public static final double rabe = ra226 * radsource_mult;
    public static final double pobe = po210 * radsource_mult;
    public static final double pube = pu238 * radsource_mult;
    // 1.7: waste-block rad at ingot mult.
    public static final double wst = 15.0D;
    public static final double wstv = 7.5D;
    public static final double fo = 10.0D;
    private Hazards() {
    }

    public static HazardData makeData(IHazardType type, double level) {
        return new HazardData().addEntry(type, level);
    }

    // One spelling of hazard/<name>; two could drift apart.
    public static TagKey<Item> hazardTag(String name) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "hazard/" + name));
    }
}
