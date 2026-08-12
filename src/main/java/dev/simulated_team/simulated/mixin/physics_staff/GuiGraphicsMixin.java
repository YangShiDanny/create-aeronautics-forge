package dev.simulated_team.simulated.mixin.physics_staff;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.simulated_team.simulated.index.SimItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {

    @Shadow public abstract int guiWidth();

    @Shadow @Final private Minecraft minecraft;

    @Shadow @Final private PoseStack pose;

    @Shadow public abstract void fill(int minX, int minY, int maxX, int maxY, int color);

    // 物理法杖光束裁剪：包裹 GuiGraphics 的 renderItem 方法（setup → 调原方法 → teardown）。
    // 关键：运行时 Minecraft 是 SRG 混淆版，方法名为 m_280638_（参数类型用官方名）。
    // 用「SRG 方法名 + 官方类型描述符 + remap=false」匹配，与本工程其它已验证可用的 @Wrap* 同模式
    // （如 BiomeMixin 的 m_47480_(...)Z）。remap=false 让 MixinExtras 直接按字面匹配，不查 refmap。
    @WrapMethod(method = "m_280638_(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;III)V", remap = false)
    private void simulated$renderPhysicsStaff(final LivingEntity entity,
                                              final ItemStack stack,
                                              final int x,
                                              final int y,
                                              final int seed,
                                              final Operation<Void> original) {
        final boolean isStaff = stack.is(SimItems.PHYSICS_STAFF.get());

        if (isStaff) {
            final Window window = Minecraft.getInstance().getWindow();
            final float scale = (float) window.getGuiScale();

            final Matrix4fc pose = this.pose.last().pose();
            final Vector3f position = pose.transformPosition(new Vector3f(x, y, 0));
            final Vector3f corner = pose.transformPosition(new Vector3f(x + 16, y + 16, 0));

            position.mul(scale);
            corner.mul(scale);

            final int slotHeight = (int) (corner.y - position.y);
            RenderSystem.enableScissor((int) position.x,
                    window.getHeight() - (int) position.y - slotHeight,
                    (int) (corner.x - position.x),
                    slotHeight);
        }

        original.call(entity, stack, x, y, seed);

        if (isStaff) {
            RenderSystem.disableScissor();
        }
    }

}
