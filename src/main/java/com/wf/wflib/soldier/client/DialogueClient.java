package com.wf.wflib.soldier.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wf.wflib.soldier.DialoguePackets;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

/** Shows what the server says in a conversation and sends back what the player picks. */
public final class DialogueClient {
    private DialogueClient() {}

    public static void node(String json) {
        JsonObject j;
        try {
            j = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return;                                                                // a damaged node is no node
        }
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof DialogueScreen open) open.update(j);
        else mc.setScreen(new DialogueScreen(j));
    }

    public static void trade(DialoguePackets.Trade packet) {
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof TradeScreen open) open.update(packet);
        else mc.setScreen(new TradeScreen(packet));
    }

    public static void close() {
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof DialogueScreen || mc.screen instanceof TradeScreen) mc.setScreen(null);
    }

    static void pick(int index) {
        PacketDistributor.sendToServer(new DialoguePackets.Pick(index));
    }

    static void move(boolean fromSoldier, int slot) {
        PacketDistributor.sendToServer(new DialoguePackets.Move(fromSoldier, slot));
    }
}
