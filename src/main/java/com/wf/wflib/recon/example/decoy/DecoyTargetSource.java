package com.wf.wflib.recon.example.decoy;

import com.wf.wflib.recon.api.ContactClass;
import com.wf.wflib.recon.api.Countermeasure;
import com.wf.wflib.recon.api.Signature;
import com.wf.wflib.recon.api.SourceIds;
import com.wf.wflib.recon.api.TargetSink;
import com.wf.wflib.recon.api.TargetSource;
import com.wf.wflib.recon.example.CornerReflector;
import com.wf.wflib.recon.api.TargetSnapshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.List;
import com.wf.wflib.recon.api.Decoy;
import com.wf.wflib.recon.api.Decoys;

/** A worked {@link TargetSource}: how an addon adds a whole new kind of target. */
public final class DecoyTargetSource implements TargetSource {

    /** Every decoy carries one, so a cheap object presents like an expensive one. */
    private static final Countermeasure[] REFLECTOR = {CornerReflector.STANDARD};

    @Override
    public void collect(ServerLevel level, AABB volume, TargetSink sink) {
        long gameTime = level.getGameTime();
        List<Decoy> decoys = Decoys.get(level).view(gameTime);
        for (int i = 0; i < decoys.size(); i++) {
            Decoy decoy = decoys.get(i);
            double x = decoy.xAt(gameTime);
            double y = decoy.yAt(gameTime);
            double z = decoy.zAt(gameTime);
            if (!volume.contains(x, y, z)) {
                continue;
            }
            sink.accept(new TargetSnapshot(SourceIds.ofDecoy(decoy.id()), ContactClass.UNKNOWN,
                    x, y, z, decoy.vx(), decoy.vy(), decoy.vz(),
                    Signature.radar(decoy.rcs()), REFLECTOR,
                    com.wf.wflib.recon.api.EmconState.ACTIVE, 0L, false));
        }
    }
}
