package com.wf.wflib.missile.lab;

import com.wf.wflib.labs.api.LabDefender;
import com.wf.wflib.labs.api.LabDefenders;
import com.wf.wflib.missile.MissileSystem;
import com.wf.wflib.missile.TurretInterceptorBlockEntity;
import com.wf.wflib.missile.api.MissileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** WFLib interceptor turret block, bound to the defending side's net. */
public record TurretDefender(String id, Supplier<Block> block) implements LabDefender {

    public static void registerAll() {
        LabDefenders.register(new TurretDefender("wflib:turret_interceptor", MissileSystem.TURRET_INTERCEPTOR::get));
        LabDefenders.register(new TurretDefender("wflib:turret_interceptor_supersonic",
                MissileSystem.TURRET_INTERCEPTOR_SUPERSONIC::get));
    }

    @Override
    public Site place(ServerLevel level, BlockPos at, UUID team) {
        BlockPos pos = at;
        level.setBlock(pos, block.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof TurretInterceptorBlockEntity be)) {
            throw new IllegalStateException(id + ": no interceptor block entity at " + pos);
        }
        CompoundTag tag = new CompoundTag();
        tag.putUUID("ControlId", UUID.nameUUIDFromBytes((id + "@" + pos.asLong()).getBytes(StandardCharsets.UTF_8)));
        be.loadWithComponents(tag, level.registryAccess());
        be.bindNet(team);
        UUID control = be.controlId();
        Set<UUID> fired = new HashSet<>();
        AABB box = new AABB(pos).inflate(TurretInterceptorBlockEntity.RANGE * 2.0);
        return new Site() {
            @Override
            public void tick() {
                for (MissileEntity m : level.getEntitiesOfClass(MissileEntity.class, box,
                        m -> control.equals(m.getControlId()))) {
                    fired.add(m.getUUID());
                }
            }

            @Override
            public int spent() {
                return fired.size();
            }

            @Override
            public void remove() {
                level.removeBlock(pos, false);
                if (!level.getBlockState(pos).isAir()) {
                    throw new IllegalStateException(id + " not removed at " + pos + ": " + level.getBlockState(pos));
                }
            }
        };
    }
}
