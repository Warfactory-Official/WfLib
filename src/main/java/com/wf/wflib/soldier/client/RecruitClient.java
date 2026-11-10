package com.wf.wflib.soldier.client;

import com.wf.wflib.soldier.RecruitPackets;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** What the server last said can be hired (from the PDA, and from the barracks that was last used). */
public final class RecruitClient {
    private static volatile RecruitPackets.Offers pda = new RecruitPackets.Offers("pda", false, true, 0, "", "", List.of());
    private static volatile RecruitPackets.Offers barracks = new RecruitPackets.Offers("", false, true, 0, "", "", List.of());

    private RecruitClient() {}

    public static void offers(RecruitPackets.Offers packet) {
        if (packet.where().equals("pda")) pda = packet;
        else {
            barracks = packet;
            if (packet.open() && Minecraft.getInstance().screen == null) Minecraft.getInstance().setScreen(new HireScreen());
        }
    }

    public static RecruitPackets.Offers pda() {
        return pda;
    }

    static RecruitPackets.Offers barracks() {
        return barracks;
    }

    public static void request(String where) {
        PacketDistributor.sendToServer(new RecruitPackets.Request(where));
    }

    public static void hire(String where, String offer) {
        PacketDistributor.sendToServer(new RecruitPackets.Hire(where, offer));
    }
}
