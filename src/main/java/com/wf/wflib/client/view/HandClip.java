package com.wf.wflib.client.view;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/**
 * View-space first-person tracks at uniform {@link #fps}: {@code right}/{@code left} fist x y z + quat, named prop
 * tracks (centre + quat [+ scale]), {@code cam} yaw/pitch/roll degrees (optional). x right, y up, -z forward.
 */
public final class HandClip {
    public final float fps;
    public final float duration;
    public final float[][] right, left;
    /** Empty when the source has no camera effector. */
    public final float[][] cam;
    private final Map<String, float[][]> props = new HashMap<>();

    private HandClip(JsonObject json) {
        fps = json.get("fps").getAsFloat();
        duration = json.get("duration").getAsFloat();
        right = track(json, "right");
        left = track(json, "left");
        cam = json.has("cam") ? track(json, "cam") : new float[0][];
        for (String key : json.keySet()) {
            if (!key.equals("right") && !key.equals("left") && !key.equals("cam") && json.get(key).isJsonArray()
                    && json.getAsJsonArray(key).get(0).isJsonArray()) {
                props.put(key, track(json, key));
            }
        }
    }

    public static HandClip load(ResourceManager resources, ResourceLocation id) {
        try (Reader in = resources.openAsReader(id)) {
            return parse(in);
        } catch (IOException e) {
            throw new UncheckedIOException(id.toString(), e);
        }
    }

    public static HandClip parse(Reader in) {
        return new HandClip(JsonParser.parseReader(in).getAsJsonObject());
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

    /** Prop track {@code name}; null => not baked. */
    public float[][] prop(String name) {
        return props.get(name);
    }

    private final Vector3f next = new Vector3f();
    private final Quaternionf nextRot = new Quaternionf();
    private int index;
    private float blend;

    private void locate(float seconds, int frames) {
        float f = Math.clamp(seconds * fps, 0.0f, frames - 1);
        index = Math.min((int) f, frames - 2);
        blend = f - index;
    }

    public void sample(float[][] track, float seconds, Vector3f pos, Quaternionf rot) {
        locate(seconds, track.length);
        float[] a = track[index], b = track[index + 1];
        pos.set(a[0], a[1], a[2]).lerp(next.set(b[0], b[1], b[2]), blend);
        rot.set(a[3], a[4], a[5], a[6]).nlerp(nextRot.set(b[3], b[4], b[5], b[6]), blend);
    }

    /** Prop column 7 when baked (0 hidden), else 1. */
    public float scale(float[][] track, float seconds) {
        if (track[0].length < 8) {
            return 1.0f;
        }
        locate(seconds, track.length);
        return track[index][7] + (track[index + 1][7] - track[index][7]) * blend;
    }

    /** Zero when {@link #cam} is empty. */
    public void sampleCamera(float seconds, float[] out) {
        if (cam.length < 2) {
            out[0] = out[1] = out[2] = 0.0f;
            return;
        }
        locate(seconds, cam.length);
        for (int i = 0; i < 3; i++) {
            out[i] = cam[index][i] + (cam[index + 1][i] - cam[index][i]) * blend;
        }
    }
}
