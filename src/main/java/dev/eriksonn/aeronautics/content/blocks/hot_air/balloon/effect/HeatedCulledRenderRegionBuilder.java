package dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.effect;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.eriksonn.aeronautics.index.AeroTags;
import dev.ryanhcode.sable.render.region.SimpleCulledRenderRegionBuilder;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class HeatedCulledRenderRegionBuilder extends SimpleCulledRenderRegionBuilder {
    private final BlockPos worldOrigin;
    private final LevelAccelerator accelerator;
    /**
     * Scratch vector reused across {@link #emit} calls to avoid per-vertex allocation.
     * (Not thread-safe; render runs on the render thread only.)
     */
    private final Vector3f tmpVec = new Vector3f();

    public HeatedCulledRenderRegionBuilder(final BlockPos worldOrigin, final LevelAccelerator accelerator, final int gridSize) {
        super(gridSize);
        this.worldOrigin = worldOrigin;
        this.accelerator = accelerator;
    }

    /**
     * Emits one vertex into {@code consumer}, transforming the model-space coordinate by
     * {@code matrix}. This is the Forge 1.20.1 equivalent of the 1.21
     * {@code consumer.vertex(matrix, x, y, z).uv(u, v).setNormal(n)} chain:
     * 1.20.1's {@link VertexConsumer} takes plain {@code (x, y, z)} doubles, has no
     * {@code setUv}/{@code setNormal}/{@code setColor} setters, and requires {@code endVertex()}.
     * The matrix transform is applied manually via {@link Matrix4f#transformPosition(Vector3f)}
     * so the heated-balloon geometry renders identically to upstream.
     */
    private void emit(final VertexConsumer consumer, final Matrix4f matrix,
                      final float x, final float y, final float z,
                      final int color, final float u, final float v, final Direction dir) {
        this.tmpVec.set(x, y, z);
        matrix.transformPosition(this.tmpVec);
        consumer
                .vertex(this.tmpVec.x, this.tmpVec.y, this.tmpVec.z)
                .color(color)
                .uv(u, v)
                .normal(dir.getStepX(), dir.getStepY(), dir.getStepZ())
                .endVertex();
    }

    /**
     * Renders all cubes into the specified consumer.
     *
     * @param consumer The consumer to draw cubes into
     */
    public void render( final Matrix4f matrix4f,  final VertexConsumer consumer) {
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (final Cube cube : this.getCubes()) {
            final int x0 = cube.x();
            final int y0 = cube.y();
            final int z0 = cube.z();
            final int x1 = cube.x() + cube.sizeX();
            final int y1 = cube.y() + cube.sizeY();
            final int z1 = cube.z() + cube.sizeZ();

            if (this.shouldFaceRender(cube, Direction.NORTH)) {
                final Direction dir = Direction.NORTH;
                this.emit(consumer, matrix4f, x0, y0, z0, this.getColor(x0, y0, z0), 0, 0, dir);
                this.emit(consumer, matrix4f, x0, y1, z0, this.getColor(x0, y1, z0), 0, 1, dir);
                this.emit(consumer, matrix4f, x1, y1, z0, this.getColor(x1, y1, z0), 1, 1, dir);
                this.emit(consumer, matrix4f, x1, y0, z0, this.getColor(x1, y0, z0), 1, 0, dir);
            }

            if (this.shouldFaceRender(cube, Direction.EAST)) {
                final Direction dir = Direction.NORTH;
                this.emit(consumer, matrix4f, x1, y0, z0, this.getColor(x1, y0, z0), 0, 0, dir);
                this.emit(consumer, matrix4f, x1, y1, z0, this.getColor(x1, y1, z0), 0, 1, dir);
                this.emit(consumer, matrix4f, x1, y1, z1, this.getColor(x1, y1, z1), 1, 1, dir);
                this.emit(consumer, matrix4f, x1, y0, z1, this.getColor(x1, y0, z1), 1, 0, dir);
            }

            if (this.shouldFaceRender(cube, Direction.SOUTH)) {
                final Direction dir = Direction.NORTH;
                this.emit(consumer, matrix4f, x1, y0, z1, this.getColor(x1, y0, z1), 1, 0, dir);
                this.emit(consumer, matrix4f, x1, y1, z1, this.getColor(x1, y1, z1), 1, 1, dir);
                this.emit(consumer, matrix4f, x0, y1, z1, this.getColor(x0, y1, z1), 0, 1, dir);
                this.emit(consumer, matrix4f, x0, y0, z1, this.getColor(x0, y0, z1), 0, 0, dir);
            }

            if (this.shouldFaceRender(cube, Direction.WEST)) {
                final Direction dir = Direction.NORTH;
                this.emit(consumer, matrix4f, x0, y0, z1, this.getColor(x0, y0, z1), 1, 0, dir);
                this.emit(consumer, matrix4f, x0, y1, z1, this.getColor(x0, y1, z1), 1, 1, dir);
                this.emit(consumer, matrix4f, x0, y1, z0, this.getColor(x0, y1, z0), 0, 1, dir);
                this.emit(consumer, matrix4f, x0, y0, z0, this.getColor(x0, y0, z0), 0, 0, dir);
            }

            if (this.shouldFaceRender(cube, Direction.DOWN)) {
                final Direction dir = Direction.DOWN;
                if (this.accelerator.getBlockState(pos.set(cube.x(), cube.y() - 1, cube.z()).offset(this.worldOrigin)).is(AeroTags.BlockTags.AIRTIGHT)) {
                    this.emit(consumer, matrix4f, x0, y0, z0, this.getColor(x0, y0, z0), 0, 1, dir);
                    this.emit(consumer, matrix4f, x1, y0, z0, this.getColor(x1, y0, z0), 1, 1, dir);
                    this.emit(consumer, matrix4f, x1, y0, z1, this.getColor(x1, y0, z1), 1, 0, dir);
                    this.emit(consumer, matrix4f, x0, y0, z1, this.getColor(x0, y0, z1), 0, 0, dir);
                }
            }

            if (this.shouldFaceRender(cube, Direction.UP)) {
                final Direction dir = Direction.UP;
                this.emit(consumer, matrix4f, x0, y1, z1, this.getColor(x0, y1, z1), 0, 1, dir);
                this.emit(consumer, matrix4f, x1, y1, z1, this.getColor(x1, y1, z1), 1, 1, dir);
                this.emit(consumer, matrix4f, x1, y1, z0, this.getColor(x1, y1, z0), 1, 0, dir);
                this.emit(consumer, matrix4f, x0, y1, z0, this.getColor(x0, y1, z0), 0, 0, dir);
            }
        }
    }

    private int getColor(final int x, final int y, final int z) {
//        if (y == 0) {
//            return 0x77ffffff;
//        }
        return 0xffffffff;
    }
}
