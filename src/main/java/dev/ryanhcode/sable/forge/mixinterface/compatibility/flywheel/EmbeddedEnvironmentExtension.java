package dev.ryanhcode.sable.forge.mixinterface.compatibility.flywheel;

import org.joml.Matrix4fc;

public interface EmbeddedEnvironmentExtension {
    void sable$setLightingInfo(Matrix4fc sceneMatrix, int scene, float skyLightScale);
}
