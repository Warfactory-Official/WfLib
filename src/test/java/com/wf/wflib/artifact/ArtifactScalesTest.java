package com.wf.wflib.artifact;

import org.junit.jupiter.api.Test;
import com.wf.wflib.artifact.api.Artifact;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactScalesTest {
    @Test
    void everyArtifactHasAScaleAndABodyNearTheTarget() {
        for (Artifact a : Artifact.ALL) {
            assertTrue(a.displayScale() > 0.0F, a.id());
            float body = a.bodySize();
            assertTrue(body >= 0.12F && body <= Artifact.BODY_SIZE + 0.005F, a.id() + " body " + body);
        }
    }

    @Test
    void smallModelsGrowAndBigOnesShrink() {
        assertTrue(Artifact.ITCHER.displayScale() > 2.0F);
        assertTrue(Artifact.BRACELET.displayScale() > 3.0F);
        assertTrue(Artifact.PELLICLE.displayScale() < 0.3F);
    }
}
