package com.wf.wflib.missile.api.client;

import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.gltf.AnimationDrive;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeSpin;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PoseDriver;
import com.wf.gemrender.rig.RigBuilder;
import com.wf.gemrender.rig.RigGeometry;
import com.wf.gemrender.rig.WavefrontObj;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.missile.api.MissileModels;
import com.wf.wflib.view.api.ObjModelJson;
import com.wf.wflib.view.api.Rotor;
import com.wf.wflib.view.api.Rotors;
import com.wf.wflib.view.api.client.Rig;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The missile airframe rigs, assembled from loose objs, with the parts that move hung off the parts that do not. */
public final class MissileRigs {
    /** The spin clip: every rotor turning, at its own signed multiple of the reference speed. */
    private static final String SPIN = "spin";

    private static final Map<ResourceLocation, Entry> MISSILES = new ConcurrentHashMap<>();

    private MissileRigs() {
    }

    /** @return the airframe for a missile id, or null while its meshes have yet to load or have failed to */
    @Nullable
    public static Rig missile(ResourceLocation id) {
        return MISSILES.computeIfAbsent(id, Entry::new).get();
    }

    /**
     * Declare every airframe's handle up front, so the rigs are built when resources finish loading rather than by
     * whichever visual happens to want one first, which would be an import on a flywheel task thread, mid-frame.
     */
    public static void load() {
        for (ResourceLocation id : MissileModels.ids()) {
            missile(id);
        }
    }

    private static final class Entry {
        private final ModelCache.Handle<GemRenderGltfModel> handle;
        private final float rotorReference;
        private volatile Rig rig;

        private Entry(ResourceLocation id) {
            List<Rotor> rotors = Rotors.of(id);
            this.rotorReference = rotors.isEmpty() ? 0.0f : Math.abs(rotors.get(0).degreesPerTick());
            float reference = this.rotorReference;
            this.handle = GemRenderModels.built(
                    ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "rig/missile/" + id.getPath()),
                    key -> buildMissile(id, MissileModels.model(id), rotors, reference));
        }

        @Nullable
        private Rig get() {
            GemRenderGltfModel model = handle.get();
            if (model == null) {
                return null;
            }
            Rig current = rig;
            if (current != null && current.model() == model) {
                return current;
            }
            GltfAnimation spin = model.animation(SPIN);
            current = new Rig(model,
                    spin == null || rotorReference <= 0.0f ? null
                            : AnimationDrive.cyclic(spin, 360.0f / rotorReference),
                    null, null);
            rig = current;
            return current;
        }
    }

    // --- missile rigs, assembled from loose objs ---------------------------------------------------

    private static GemRenderGltfModel buildMissile(ResourceLocation id, ResourceLocation body,
                                                   List<Rotor> rotors, float rotorReference)
            throws IOException {
        ObjModelJson hull = ObjModelJson.read(body);
        Map<ResourceLocation, Material> materials = new HashMap<>();
        Material hullMaterial = materials.computeIfAbsent(hull.texture(), MissileRigs::material);

        RigBuilder rig = new RigBuilder("missile/" + id.getPath());
        int root = rig.bone("body", RigBuilder.ROOT, 0.0f, 0.0f, 0.0f);

        int[] rotorSlots = new int[rotors.size()];
        ObjModelJson[] rotorParts = new ObjModelJson[rotors.size()];
        for (int i = 0; i < rotors.size(); i++) {
            Vector3f pivot = Rotors.pivot(rotors.get(i));
            rotorSlots[i] = rig.bone("prop" + i, root, pivot.x, pivot.y, pivot.z);
            rotorParts[i] = ObjModelJson.read(rotors.get(i)
                    .model());
        }

        NodeTable table = rig.table();

        rig.attachAll(root, groups(hull), null);
        for (int i = 0; i < rotorSlots.length; i++) {
            rig.attachAll(rotorSlots[i], groups(rotorParts[i]),
                    partMaterial(materials, rotorParts[i], hull));
        }

        Map<String, GltfAnimation> clips = new LinkedHashMap<>();
        if (!rotors.isEmpty() && rotorReference > 0.0f) {
            List<PoseDriver> spin = new ArrayList<>(rotors.size());
            for (int i = 0; i < rotors.size(); i++) {
                Rotor rotor = rotors.get(i);
                Vector3f axis = rotor.axis();
                spin.add(NodeSpin.about(table, rotorSlots[i], axis.x, axis.y, axis.z,
                        rotor.degreesPerTick() / rotorReference));
            }
            clips.put(SPIN, GltfAnimation.procedural(SPIN, 1.0f, spin.toArray(new PoseDriver[0])));
        }

        return rig.build(hullMaterial, clips);
    }

    private static Map<String, RigGeometry> groups(ObjModelJson model) throws IOException {
        return WavefrontObj.load(model.obj());
    }

    @Nullable
    private static Material partMaterial(Map<ResourceLocation, Material> materials, ObjModelJson part,
                                         ObjModelJson hull) {
        if (part.texture()
                .equals(hull.texture())) {
            return null;
        }
        return materials.computeIfAbsent(part.texture(), MissileRigs::material);
    }

    private static Material material(ResourceLocation texture) {
        return SimpleMaterial.builder()
                .texture(texture)
                .mipmap(false)
                .build();
    }
}
