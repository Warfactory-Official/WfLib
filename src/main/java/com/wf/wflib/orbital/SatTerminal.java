package com.wf.wflib.orbital;

import com.wf.wflib.orbital.api.OrbitalNet;
import com.wf.wflib.orbital.api.SatView;
import com.wf.wflib.pda.api.TerminalNoun;
import com.wf.wflib.pda.api.Uplink;
import com.wf.wflib.recon.api.ReconOwners;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** PDA Terminal {@code sats}: own net, uplink required (as the Satellites app). */
final class SatTerminal implements TerminalNoun {
    static final SatTerminal INSTANCE = new SatTerminal();

    private SatTerminal() {
    }

    @Override
    public String plural() {
        return "sats";
    }

    @Override
    public String singular() {
        return "sat";
    }

    @Override
    public String none() {
        return "no satellites on this net";
    }

    @Override
    @Nullable
    public List<Entry> entries(ServerPlayer player) {
        long net = ReconOwners.netOf(player);
        if (!Uplink.allows(player, net)) {
            return null;
        }
        List<Entry> out = new ArrayList<>();
        for (SatView s : OrbitalNet.onNet(player.serverLevel(), net)) {
            out.add(new Entry() {
                @Override
                public String name() {
                    return s.callsign();
                }

                @Override
                public String line(ServerPlayer viewer) {
                    return s.callsign() + "  " + s.payload().getPath();
                }

                @Override
                public String rename(ServerPlayer p, @Nullable String name) {
                    if (name == null) {
                        return "err: satellites need a callsign";
                    }
                    return OrbitalNet.commandBy(p.serverLevel(), s.id(), p.getGameProfile().getName(), "callsign",
                            new String[]{name});
                }
            });
        }
        return out;
    }
}
