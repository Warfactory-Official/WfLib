package com.wf.wflib.view.api.client;

import com.wf.gemrender.gltf.AnimationDrive;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import org.jetbrains.annotations.Nullable;

/**
 * An airframe together with the drives that turn what the game measures into clip-local time.
 *
 * @param spin null for a model with no rotors, which is most missiles
 * @param stow null for a model that draws everything it has
 * @param piece null for anything but wreckage, which is the one thing drawn as a part of a model
 *      rather than as the whole of one
 */
public record Rig(GemRenderGltfModel model, @Nullable AnimationDrive spin, @Nullable AnimationDrive stow,
                  @Nullable AnimationDrive piece) {
}
