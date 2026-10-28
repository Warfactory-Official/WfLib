package com.wf.wflib.smoking.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wf.wflib.WFLib;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;

/** {@code assets/wflib/smoking/cigarette.json} (tools/smoking/bake_cigarette.py): view-space tracks, uniform {@link #fps}. */
final class SmokingClip {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "smoking/cigarette.json");

    final float fps;
    final float duration;
    /** Fist: x, y, z, qx, qy, qz, qw per frame. */
    final float[][] right, left, pack, cig;
    /** Yaw, pitch, roll degrees per frame. */
    final float[][] cam;

    private SmokingClip(JsonObject json) {
        fps = json.get("fps").getAsFloat();
        duration = json.get("duration").getAsFloat();
        right = track(json, "right");
        left = track(json, "left");
        pack = track(json, "pack");
        cig = track(json, "cig");
        cam = track(json, "cam");
    }

    static SmokingClip load(ResourceManager resources) {
        try (Reader in = resources.openAsReader(ID)) {
            return parse(in);
        } catch (IOException e) {
            throw new UncheckedIOException(ID.toString(), e);
        }
    }

    static SmokingClip parse(Reader in) {
        return new SmokingClip(JsonParser.parseReader(in).getAsJsonObject());
    }

    private static float[][] track(JsonObject json, String name) {
        JsonArray rows = json.getAsJsonArray(name);
        float[][] out = new float[rows.size()][];
        for (int i = 0; i < out.length; i++) {
            JsonArray row = rows.get(i).getAsJsonArray();
            out[i] = new float[row.size()];
            for (int k = 0; k < out[i].length; k++) {
                out[i][k] = row.get(k).getAsFloat();
            }
        }
        return out;
    }

    private final Vector3f next = new Vector3f();
    private final Quaternionf nextRot = new Quaternionf();
    private int index;
    private float blend;

    /** Frame pair and blend at {@code seconds}, clamped to the clip. */
    private void locate(float seconds, int frames) {
        float f = Math.clamp(seconds * fps, 0.0f, frames - 1);
        index = Math.min((int) f, frames - 2);
        blend = f - index;
    }

    void sample(float[][] track, float seconds, Vector3f pos, Quaternionf rot) {
        locate(seconds, track.length);
        float[] a = track[index], b = track[index + 1];
        pos.set(a[0], a[1], a[2]).lerp(next.set(b[0], b[1], b[2]), blend);
        rot.set(a[3], a[4], a[5], a[6]).nlerp(nextRot.set(b[3], b[4], b[5], b[6]), blend);
    }

    void sampleCamera(float seconds, float[] out) {
        locate(seconds, cam.length);
        for (int i = 0; i < 3; i++) {
            out[i] = cam[index][i] + (cam[index + 1][i] - cam[index][i]) * blend;
        }
    }
}
