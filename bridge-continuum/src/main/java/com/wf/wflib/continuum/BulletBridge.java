package com.wf.wflib.continuum;

import com.hbm.entity.projectile.EntityBulletBaseMK4;
import com.hbm.items.weapon.sedna.BulletConfig;
import com.hbm.items.weapon.sedna.BulletConfigs;
import com.hbm.lib.ModDamageSources.DamageClass;
import com.wf.wflib.damage.api.StrikeContext;
import com.wf.wflib.damage.api.Threat;
import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.api.KineticPresets;
import com.wf.wflib.round.api.Rounds;
import com.wf.wflib.warhead.api.Warheads;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Continuum's bullets as WFLib's (docs, 3.7). Two layers:
 * <ul>
 * <li>Every Continuum bullet in flight carries a {@link Threat} to anything that asks the damage source for a {@link StrikeContext}: hull armour walks its
 * penetration, plates wear by it, the labs record it. That needs no change to the bullet.</li>
 * <li>A <em>plain</em> bullet, one whose behaviour is only "hurt what it hits, bounce off shallow angles" (no custom flight or impact lambda, no steering), is not
 * flown as Continuum's entity at all: its spawn is turned into a WFLib round with the same speed, gravity, life, ricochet and armour-piercing, so it gets WFLib's
 * flight (lag compensation, terrain, tracer streaks). Anything with its own behaviour (explosive and incendiary loads, rockets, flames, beams) stays Continuum's.</li>
 * </ul>
 */
final class BulletBridge {
    private static final Map<BulletConfig, Ballistics.Profile> PROFILES = new IdentityHashMap<>();
    private static final Map<BulletConfig, KineticPreset> PRESETS = new IdentityHashMap<>();

    private BulletBridge() {
    }

    static void install(boolean replace) {
        for (BulletConfig config : BulletConfigs.all()) {
            Ballistics.Profile profile = Ballistics.of(config);
            PROFILES.put(config, profile);
            if (!replace || !plain(config)) continue;
            KineticPreset preset = preset(config, profile);
            KineticPresets.registerItemless(preset);
            PRESETS.put(config, preset);
        }
        StrikeContext.ADAPTERS.add(BulletBridge::adapt);
        NeoForge.EVENT_BUS.addListener(BulletBridge::onJoin);
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) NeoForge.EVENT_BUS.addListener(BulletBridge::onTooltip);
        ContinuumBridge.LOG.info("Bullets: {} Continuum rounds carry a threat, {} are flown as WFLib rounds", PROFILES.size(), PRESETS.size());
    }

    static @Nullable Ballistics.Profile profile(BulletConfig config) {
        return PROFILES.get(config);
    }

    static @Nullable KineticPreset presetOf(BulletConfig config) {
        return PRESETS.get(config);
    }

    /** A round whose behaviour is all in the numbers: nothing of Continuum's own would be lost by flying it as a WFLib round. */
    static boolean plain(BulletConfig c) {
        return (c.pType == BulletConfig.ProjectileType.BULLET || c.pType == BulletConfig.ProjectileType.BULLET_CHUNKLOADING)
                && c.onUpdate == null && c.onImpact == null && !c.isSpectral && c.impactsEntities
                && c.onEntityHit == BulletConfig.LAMBDA_STANDARD_ENTITY_HIT && c.onRicochet == BulletConfig.LAMBDA_STANDARD_RICOCHET
                && c.dmgClass == DamageClass.PHYSICAL && c.projectilesMax <= 1 && c.id != null;
    }

    private static KineticPreset preset(BulletConfig c, Ballistics.Profile p) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ContinuumBridge.ID, c.id.getPath());
        var b = KineticPreset.builder(id, null, Warheads.rl("inert"))
                .speed(c.velocity).gravity(c.gravity).life(Math.max(1, c.expires))
                .caliber(p.caliberMm()).armorPenetration(p.penMm()).threat(p.kind())
                .impactDamage(1.0)                          // the launch scales it: the bullet's own damage is what the gun worked out
                .pierce(Math.max(0.0f, c.armorThresholdNegation), Math.max(0.0f, Math.min(1.0f, c.armorPiercingPercent)))
                .headshot(Math.max(1.0f, c.headshotMult))
                .ricochet(Math.max(60.0, Math.min(89.0, 90.0 - c.ricochetAngle)))
                .tracer(p.tracerRgb())
                .noChunkLoading();
        if (c.doesPenetrate) b.passesThroughEntities();
        return b.build();
    }

    /** A damage source struck by one of Continuum's bullets: its threat, travel and shooter. */
    private static @Nullable StrikeContext adapt(DamageSource source) {
        if (!(source.getDirectEntity() instanceof EntityBulletBaseMK4 bullet) || bullet.config == null) return null;
        Ballistics.Profile profile = PROFILES.get(bullet.config);
        return profile == null ? null : new Strike(bullet, profile.threat());
    }

    private record Strike(EntityBulletBaseMK4 bullet, Threat threat) implements StrikeContext {
        @Override
        public Vec3 position() {
            return bullet.position();
        }

        @Override
        public Vec3 velocity() {
            return bullet.getDeltaMovement().scale(bullet.step());
        }

        @Override
        public Object key() {
            return bullet;
        }

        @Override
        public @Nullable Entity shooter() {
            return bullet.getThrower();
        }
    }

    /** Ammunition item to the rounds it loads (an item can load several: a magazine of one cartridge is one item, its loads are items of their own). */
    private static Map<net.minecraft.world.item.Item, BulletConfig> byAmmo;

    /** The round's bore and penetration on its ammunition: the numbers every other WFLib round shows, which Continuum has none of. */
    private static void onTooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
        if (byAmmo == null) {
            Map<net.minecraft.world.item.Item, BulletConfig> map = new IdentityHashMap<>();
            for (BulletConfig c : PROFILES.keySet()) {
                if (c.ammo != null && c.ammo.get() != null) map.putIfAbsent(c.ammo.get(), c);
            }
            byAmmo = map;
        }
        BulletConfig config = byAmmo.get(event.getItemStack().getItem());
        if (config == null) return;
        Ballistics.Profile p = PROFILES.get(config);
        var tooltip = event.getToolTip();
        tooltip.add(net.minecraft.network.chat.Component.literal(String.format("Penetration %.1f mm steel", p.penMm())).withStyle(net.minecraft.ChatFormatting.GOLD)
                .append(net.minecraft.network.chat.Component.literal(String.format("  bore %.1f mm  %s", p.caliberMm(), p.kind().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '))).withStyle(net.minecraft.ChatFormatting.GRAY)));
        if (config.armorThresholdNegation > 0.0F || config.armorPiercingPercent > 0.0F) {
            tooltip.add(net.minecraft.network.chat.Component.literal(String.format("Ignores %.0f armour threshold, %.0f%% of resistance", config.armorThresholdNegation, config.armorPiercingPercent * 100.0F))
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof EntityBulletBaseMK4 bullet) || event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level)) return;
        if (bullet.config == null || bullet.accel != 0.0 || bullet.lockonTarget != null) return;
        KineticPreset preset = PRESETS.get(bullet.config);
        if (preset == null) return;
        Vec3 heading = bullet.getDeltaMovement();
        if (heading.lengthSqr() < 1.0e-8) return;
        Rounds.launch(level, preset, bullet.position(), heading.normalize().scale(bullet.step()), bullet.getThrower(), null, Math.max(0.0f, bullet.damage), 0, Rounds.NO_SEQ);
        event.setCanceled(true);
    }
}
