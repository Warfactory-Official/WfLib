package com.wf.wflib.hazard;

import com.wf.wflib.hazard.ContaminationUtil.ContaminationType;
import com.wf.wflib.hazard.ContaminationUtil.HazardType;
import com.wf.wflib.radiation.RadiationConfig;
import com.wf.wflib.radiation.RadiationSystemNT;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Random;

/**
 * NTM EntityEffectHandler, hazard subset: dose buffer + sync, contamination, field dose, sickness, digamma, lungs.
 * Out of scope (other NTM systems): bomb timer, oil, fire/phosphorus/balefire, MKU contagion, crater biomes,
 * pollution soot. Divergence: NTM vomit/sweat FX -> vanilla particles; nuclear creeper/duck conversions absent.
 */
public final class HazardEntityEffects {

    private static final int TICKS_PER_SECOND = 20;

    private HazardEntityEffects() {
    }

    public static void onUpdate(LivingEntity entity) {
        if (entity.level().isClientSide()) return;

        LivingHazardProps props = LivingHazardProps.peek(entity);

        if (props != null && entity.tickCount % TICKS_PER_SECOND == 0) {
            props.radBuf = props.radEnv;
            props.radEnv = 0D;
            // Unnegotiated channel => send throws; FakePlayer connection has no netty channel.
            if (entity instanceof ServerPlayer sp && !(sp instanceof FakePlayer)
                    && sp.connection.hasChannel(HazardSyncPacket.TYPE)) {
                PacketDistributor.sendToPlayer(sp, HazardSyncPacket.of(props));
            }
        }

        if (props != null) handleContamination(entity, props);
        handleRadiation(entity);
        if (props != null) {
            // Pushed every tick: ArmorExposure forgets contact not re-pushed within its interval.
            if (entity instanceof Player && props.radBuf > 0D) HazardProtection.exposeArmor(entity, props.radBuf);
            handleRadiationEffect(entity, props);
            handleRadiationFX(entity, props);
            handleDigamma(entity, props);
            handleLungDisease(entity, props);
        }
    }

    private static void handleContamination(LivingEntity entity, LivingHazardProps props) {
        List<ContaminationEffect> cont = props.getCont();
        if (cont.isEmpty()) return;
        var it = cont.iterator();
        while (it.hasNext()) {
            ContaminationEffect con = it.next();
            ContaminationUtil.contaminate(entity, HazardType.RADIATION,
                    con.ignoreArmor ? ContaminationType.RAD_BYPASS : ContaminationType.CREATIVE, con.getRad());
            con.time--;
            if (con.time <= 0) it.remove();
        }
    }

    private static void handleRadiation(LivingEntity entity) {
        if (ContaminationUtil.isRadImmune(entity)) return;
        if (!(entity.level() instanceof ServerLevel server)) return;
        double rad = RadiationSystemNT.doseAt(server, entity.blockPosition());
        if (rad > 0D) {
            ContaminationUtil.contaminate(entity, HazardType.RADIATION, ContaminationType.CREATIVE,
                    rad / TICKS_PER_SECOND);
        }
    }

    private static void handleRadiationEffect(LivingEntity entity, LivingHazardProps props) {
        if (!RadiationConfig.enableContamination) return;
        if (entity.isDeadOrDying()) return;
        if (!(entity.level() instanceof ServerLevel world)) return;
        if (entity instanceof Player p && (p.isCreative() || p.isSpectator())) return;

        double eRad = props.radiation();
        if (eRad <= 0D) return;
        DamageSource radSource = world.damageSources().source(HazardContent.DAMAGE_RADIATION);
        if (entity.isInvulnerableTo(radSource)) return;

        if (eRad >= 200D && entity.getClass() == Creeper.class) {
            if (world.getRandom().nextInt(3) != 0) {
                entity.hurt(radSource, 100F);
                return;
            }
        } else if (eRad >= 50D && entity instanceof Cow && !(entity instanceof MushroomCow)) {
            if (replaceWith(entity, EntityType.MOOSHROOM)) return;
        } else if (eRad >= 500D && entity instanceof Villager) {
            if (replaceWith(entity, EntityType.ZOMBIE_VILLAGER)) return;
        } else if (eRad >= 800D && entity instanceof Horse) {
            if (replaceWith(entity, EntityType.ZOMBIE_HORSE)) return;
        }

        if (eRad < 200D || ContaminationUtil.isRadImmune(entity)) return;

        if (eRad >= 1000D) {
            entity.hurt(radSource, 1000F);
            LivingHazardProps.setRadiation(entity, 0D);
            if (entity.getHealth() > 0F) entity.setHealth(0F);
            return;
        }

        int rng = world.getRandom().nextInt(21000);
        if (eRad >= 800D) {
            if (rng % 300 == 0) entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 5 * 30, 0));
            if (rng % 300 == 50) entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10 * TICKS_PER_SECOND, 2));
            if (rng % 300 == 100) entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 10 * TICKS_PER_SECOND, 2));
            if (rng % 500 == 0) entity.addEffect(new MobEffectInstance(MobEffects.POISON, 3 * TICKS_PER_SECOND, 2));
            if (rng % 700 == 0) entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 3 * TICKS_PER_SECOND, 1));
        } else if (eRad >= 600D) {
            if (rng % 300 == 0) entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 5 * 30, 0));
            if (rng % 300 == 50) entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10 * TICKS_PER_SECOND, 2));
            if (rng % 300 == 100) entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 10 * TICKS_PER_SECOND, 2));
            if (rng % 500 == 0) entity.addEffect(new MobEffectInstance(MobEffects.POISON, 3 * TICKS_PER_SECOND, 1));
        } else if (eRad >= 400D) {
            if (rng % 300 == 0) entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 5 * 30, 0));
            if (rng % 500 == 50) entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5 * TICKS_PER_SECOND, 0));
            if (rng % 300 == 100) entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 5 * TICKS_PER_SECOND, 1));
        } else {
            if (rng % 300 == 0) entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 5 * TICKS_PER_SECOND, 0));
            if (rng % 500 == 0) entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 5 * TICKS_PER_SECOND, 0));
        }
    }

    private static void handleRadiationFX(LivingEntity entity, LivingHazardProps props) {
        if (ContaminationUtil.isRadImmune(entity)) return;
        if (!(entity.level() instanceof ServerLevel world)) return;
        if (entity instanceof Player p && (p.isCreative() || p.isSpectator())) return;

        double rad = props.radiation();
        if (rad <= 200D) return;

        // Window offsets fixed per entity, not rerolled per tick.
        Random offsets = new Random(entity.getId());
        int r600 = offsets.nextInt(600);
        int r1200 = offsets.nextInt(1200);
        long time = world.getGameTime();

        if (rad > 600D) {
            if ((time + r600) % 600 < 20 && canVomit(entity)) {
                vomit(world, entity, Blocks.REDSTONE_BLOCK.defaultBlockState(), 25);
                if ((time + r600) % 600 == 1) retch(world, entity);
            }
        } else if ((time + r1200) % 1200 < 20 && canVomit(entity)) {
            vomit(world, entity, Blocks.LIME_TERRACOTTA.defaultBlockState(), 15);
            if ((time + r1200) % 1200 == 1) retch(world, entity);
        }

        if (rad > 900D && (time + offsets.nextInt(10)) % 10 == 0) {
            sweat(world, entity, Blocks.REDSTONE_BLOCK.defaultBlockState());
        }
    }

    private static void handleDigamma(LivingEntity entity, LivingHazardProps props) {
        if (props.digamma < 0.01D || !(entity.level() instanceof ServerLevel world)) return;
        int chance = Math.max(10 - (int) props.digamma, 1);
        if (chance == 1 || entity.getRandom().nextInt(chance) == 0) {
            sweat(world, entity, Blocks.SOUL_SAND.defaultBlockState());
        }
    }

    private static void handleLungDisease(LivingEntity entity, LivingHazardProps props) {
        if (!(entity.level() instanceof ServerLevel world)) return;
        if (entity instanceof Player p && p.isCreative()) {
            LivingHazardProps.setBlackLung(entity, 0);
            LivingHazardProps.setAsbestos(entity, 0);
            return;
        }

        int bl = props.blackLung();
        if (bl > 0 && bl < LivingHazardProps.maxBlacklung * 0.5D) {
            LivingHazardProps.setBlackLung(entity, bl - 1);
        }

        double blacklung = Math.min(props.blackLung(), LivingHazardProps.maxBlacklung);
        double asbestos = Math.min(props.asbestos(), LivingHazardProps.maxAsbestos);
        boolean coughs = blacklung / LivingHazardProps.maxBlacklung > 0.25D
                || asbestos / LivingHazardProps.maxAsbestos > 0.25D;
        if (!coughs) return;

        double blacklungDelta = 1D - (blacklung / (double) LivingHazardProps.maxBlacklung);
        double asbestosDelta = 1D - (asbestos / (double) LivingHazardProps.maxAsbestos);
        double total = 1D - (blacklungDelta * asbestosDelta);

        if (total > 0.75D) entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 2));
        if (total > 0.95D) entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));

        int freq = Math.max((int) (1000 - 950 * total), 20);
        if (world.getGameTime() % freq == entity.getId() % freq) {
            world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    HazardContent.COUGH.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            double blacklungShare = blacklung / LivingHazardProps.maxBlacklung;
            if (asbestos / LivingHazardProps.maxAsbestos > 0.75D || blacklungShare > 0.75D) {
                vomit(world, entity, Blocks.REDSTONE_BLOCK.defaultBlockState(), 5);
            }
            if (blacklungShare > 0.5D) {
                spray(world, entity, ParticleTypes.LARGE_SMOKE, blacklungShare > 0.8D ? 50 : 10);
            }
        }
    }

    private static boolean canVomit(LivingEntity entity) {
        return entity.getType().getCategory() != MobCategory.WATER_CREATURE;
    }

    private static void retch(ServerLevel world, LivingEntity entity) {
        world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                HazardContent.VOMIT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        entity.addEffect(new MobEffectInstance(MobEffects.HUNGER, 60, 19));
    }

    private static void vomit(ServerLevel world, LivingEntity entity, BlockState state, int count) {
        spray(world, entity, new BlockParticleOption(ParticleTypes.BLOCK, state), count);
    }

    /** Forward from the mouth. */
    private static void spray(ServerLevel world, LivingEntity entity, ParticleOptions particle, int count) {
        var look = entity.getLookAngle();
        world.sendParticles(particle, entity.getX() + look.x * 0.3D, entity.getEyeY() - 0.15D,
                entity.getZ() + look.z * 0.3D, count, look.x * 0.1D, 0.05D, look.z * 0.1D, 0.15D);
    }

    private static void sweat(ServerLevel world, LivingEntity entity, BlockState state) {
        world.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), entity.getX(),
                entity.getY() + entity.getBbHeight() * 0.5D, entity.getZ(), 1,
                entity.getBbWidth() * 0.5D, entity.getBbHeight() * 0.5D, entity.getBbWidth() * 0.5D, 0.0D);
    }

    private static boolean replaceWith(LivingEntity src, EntityType<? extends Mob> type) {
        if (!(src instanceof Mob mob)) return false;
        Entity replacement = mob.convertTo(type, true);
        return replacement != null;
    }
}
