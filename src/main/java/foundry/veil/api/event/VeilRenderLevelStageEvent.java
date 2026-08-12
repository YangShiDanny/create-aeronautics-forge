package foundry.veil.api.event;
public final class VeilRenderLevelStageEvent {
    public enum Stage {
        AFTER_SOLID_BLOCKS,
        AFTER_CUTOUT_MIPPED_BLOCKS,
        AFTER_TRANSLUCENT_BLOCKS,
        AFTER_BLOCK_ENTITIES,
        AFTER_PARTICLES,
        AFTER_WEATHER,
        AFTER_LEVEL
    }
}
