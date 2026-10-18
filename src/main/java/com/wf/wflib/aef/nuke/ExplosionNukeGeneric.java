package com.wf.wflib.aef.nuke;

import com.wf.wflib.damage.WFDamage;
import com.wf.wflib.damage.WFDamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// NTM-next: NUCLEAR_BLAST type + armour DT pierce 100 => wflib:explosive, no pierce. NTM projectile/ocelot exemptions
// => any Projectile. Gore on kill dropped.
public final class ExplosionNukeGeneric {

    private ExplosionNukeGeneric() {
    }

    public static void dealDamage(Level world, double x, double y, double z, double radius) {
        dealDamage(world, x, y, z, radius, 250F);
    }

    public static void dealDamage(Level world, double x, double y, double z, double radius, float maxDamage) {
        AABB box = new AABB(x, y, z, x, y, z).inflate(radius);
        DamageSource source = world.damageSources().source(WFDamageTypes.EXPLOSIVE);

        for (Entity e : world.getEntities((Entity) null, box)) {
            double dist = Math.sqrt(e.distanceToSqr(x, y, z));
            if (dist > radius) continue;

            double entX = e.getX();
            double entY = e.getY() + e.getEyeHeight();
            double entZ = e.getZ();

            if (isExplosionExempt(e) || isObstructed(world, e, x, y, z, entX, entY, entZ)) continue;

            float damage = (float) (maxDamage * (radius - dist) / radius);
            boolean doKnockback = true;
            if (e instanceof LivingEntity living) {
                if (!living.isAlive()) continue;
                doKnockback = WFDamage.hurtIgnoringIFrames(living, source, damage);
            } else {
                e.hurt(source, damage);
            }

            e.igniteForSeconds(5F);

            if (doKnockback) {
                double knockX = entX - x;
                double knockY = entY - y;
                double knockZ = entZ - z;
                double len = Math.sqrt(knockX * knockX + knockY * knockY + knockZ * knockZ);
                if (len != 0D) {
                    e.setDeltaMovement(e.getDeltaMovement().add(knockX / len * 0.2D, knockY / len * 0.2D,
                            knockZ / len * 0.2D));
                }
            }
        }
    }

    private static boolean isExplosionExempt(Entity e) {
        return e instanceof Projectile || e instanceof Player player && player.isCreative();
    }

    private static boolean isObstructed(Level world, Entity e, double x, double y, double z, double a, double b,
                                        double c) {
        ClipContext ctx = new ClipContext(new Vec3(x, y, z), new Vec3(a, b, c),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e);
        return world.clip(ctx).getType() != HitResult.Type.MISS;
    }
}
