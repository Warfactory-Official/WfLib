package com.wf.wflib.colony.glyphid;

import com.wf.wflib.colony.api.EntityGlyphid;
import com.wf.wflib.colony.api.GlyphidCaste;
import com.wf.wflib.colony.api.GlyphidTasks;
import com.wf.wflib.colony.api.SimGlyphid;
import com.wf.wflib.colony.api.SimGlyphidManager;
import com.wf.wflib.recon.api.ContactClass;
import com.wf.wflib.recon.api.EmconState;
import com.wf.wflib.recon.api.EntityTargetSource;
import com.wf.wflib.recon.api.Signature;
import com.wf.wflib.recon.api.SignatureProvider;
import com.wf.wflib.recon.api.SignatureProviders;
import com.wf.wflib.recon.api.SourceIds;
import com.wf.wflib.recon.api.TargetSink;
import com.wf.wflib.recon.api.TargetSnapshot;
import com.wf.wflib.recon.api.TargetSource;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** What glyphids look like to a sensor, loaded as entities or off the world as records. */
public final class GlyphidRecon {
    /** Reference cross-section of a one-block-wide target, so a grunt lands near a quarter and a behemoth near one. */
    private static final float BODY_RCS = 0.25f;
    /** How much each armour plate adds. */
    private static final float ARMOR_GAIN = 0.15f;
    /** Seismic energy per point of dig strength. */
    private static final float DIG_SEISMIC = 0.5f;
    /** Thermal per square block of body. */
    private static final float BODY_THERMAL = 0.15f;
    /** Blocks below the surface at which a digger stops being a radar target and becomes a seismic one. */
    private static final int BURIED_DEPTH = 2;

    private GlyphidRecon() {
    }

    /** @param armorBits how many armour plates are intact, 0-5. */
    public static Signature signature(GlyphidCaste caste, int armorBits) {
        double scale = caste.scale();
        float rcs = (float) (BODY_RCS * scale * scale * (1.0f + ARMOR_GAIN * armorBits));
        float seismic = (float) (caste.stats().digStrength() * DIG_SEISMIC);
        float thermal = (float) (BODY_THERMAL * scale * scale);
        return new Signature(rcs, seismic, thermal, 0.0f, 0.0f);
    }

    public static Signature signature(EntityGlyphid glyphid) {
        return signature(GlyphidCaste.byType(glyphid.getType()), Integer.bitCount(glyphid.armor() & 0xFF));
    }

    /** Loaded glyphids. */
    public static final class Loaded implements TargetSource {
        @Override
        public void collect(ServerLevel level, AABB volume, TargetSink sink) {
            List<EntityGlyphid> glyphids = level.getEntitiesOfClass(EntityGlyphid.class, volume, Entity::isAlive);
            for (int i = 0; i < glyphids.size(); i++) {
                EntityGlyphid glyphid = glyphids.get(i);
                sink.accept(EntityTargetSource.snapshot(glyphid, ContactClass.GLYPHID, SignatureProviders.of(glyphid),
                        EmconState.ACTIVE, 0L, buried(level, glyphid)));
            }
        }

        private static boolean buried(ServerLevel level, EntityGlyphid glyphid) {
            if (glyphid.getCurrentTask() != GlyphidTasks.TASK_DIG) {
                return false;
            }
            int x = glyphid.getBlockX();
            int z = glyphid.getBlockZ();
            LevelChunk chunk = level.getChunkSource().getChunkNow(
                    SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
            return chunk != null
                    && glyphid.getBlockY() < chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - BURIED_DEPTH;
        }
    }

    /** Glyphids that are off the world, walking as records rather than entities. */
    public static final class Simulated implements TargetSource {
        @Override
        public void collect(ServerLevel level, AABB volume, TargetSink sink) {
            List<SimGlyphid> glyphids = SimGlyphidManager.tier(level).view();
            for (int i = 0; i < glyphids.size(); i++) {
                SimGlyphid glyphid = glyphids.get(i);
                if (!volume.contains(glyphid.x, glyphid.y, glyphid.z)) {
                    continue;
                }
                sink.accept(TargetSnapshot.of(SourceIds.ofGlyphid(glyphid.id), ContactClass.GLYPHID,
                        glyphid.x, glyphid.y, glyphid.z,
                        glyphid.x - glyphid.prevX, glyphid.y - glyphid.prevY, glyphid.z - glyphid.prevZ,
                        signature(glyphid.caste, Integer.bitCount(glyphid.armor & EntityGlyphid.FULL_ARMOR)),
                        0L, false));
            }
        }
    }

    public static final class Signatures implements SignatureProvider {
        @Override
        @Nullable
        public Signature of(Entity entity) {
            return entity instanceof EntityGlyphid glyphid ? signature(glyphid) : null;
        }
    }
}
