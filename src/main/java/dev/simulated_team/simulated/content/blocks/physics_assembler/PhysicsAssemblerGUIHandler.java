package dev.simulated_team.simulated.content.blocks.physics_assembler;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SableDistUtil;
import dev.simulated_team.simulated.index.SimGUITextures;
import dev.simulated_team.simulated.index.SimSoundEvents;
import dev.simulated_team.simulated.network.packets.AssemblePacket;
import dev.simulated_team.simulated.util.hold_interaction.BlockHoldInteraction;
import foundry.veil.api.network.VeilPacketManager;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import dev.simulated_team.simulated.network.SimPacketManager;

public class PhysicsAssemblerGUIHandler extends BlockHoldInteraction {
    private static final double PULLED_THRESHOLD = 0.015;
    public static int lastSignal = 0;
    public static float animatedVelocity;
    public static float animatedValue;
    public static float lastAnimatedValue;
    // [fix] 物理化后组装器位于子关卡，getClientLevel() 取不到其方块实体；
    // 改为在 startHold 时捕获引用，供 activeOnMouseMove/release/activeTick 复用（与 SteeringWheelHandler 同款做法）。
    private static PhysicsAssemblerBlockEntity targetAssembler = null;


    @Override
    public void startHold(final Level level, final Player player, final BlockPos blockPos) {
        super.startHold(level, player, blockPos);

        final BlockEntity be = level.getBlockEntity(blockPos);
        if (!(be instanceof final PhysicsAssemblerBlockEntity assembler)) {
            PhysicsAssemblerGUIHandler.targetAssembler = null;
            return;
        }

        PhysicsAssemblerGUIHandler.targetAssembler = assembler; // [fix] 捕获引用
        animatedValue = 0.0f;
        // [第19轮] 客户端判定「是否已物理化」不能用裸 getContaining(移植架构下恒 null)，
        // 改用 BE 的双端反查 findOwningSubLevel，物理化后拉杆条正确从 1.0(已拉下)开始。
        if (assembler.findOwningSubLevel() != null) animatedValue = 1.0f;
        animatedVelocity = 0.0f;
    }

    @Override
    public void release() {
        final PhysicsAssemblerBlockEntity be = PhysicsAssemblerGUIHandler.targetAssembler;
        if (be == null) {
            return;
        }
        if (be.holdingLever) return;

        // [fix] 用实体判定是否在子关卡（getSubLevelHolding() 按坐标在物理化后取不到 → inPlot 恒 false）
        boolean inPlot = be.findOwningSubLevel() != null; // [第19轮] 双端反查，物理化后为 true -> 只有「拉回 0」才发拆解包

        if ((inPlot && animatedValue < PULLED_THRESHOLD && lastAnimatedValue < PULLED_THRESHOLD) ||
                (!inPlot && lastAnimatedValue > 1.0 - PULLED_THRESHOLD && animatedValue > 1.0 - PULLED_THRESHOLD)) {
            dev.simulated_team.simulated.network.SimPacketManager.INSTANCE.server().sendPacket(new AssemblePacket(this.getInteractionPos()));
            inPlot = !inPlot;
            be.setClientHoldLeverInPlace(true);
        }

        be.visualAngle.setValue(animatedValue * 45.0);
        be.clientFlickLeverTo(inPlot);
        be.stopControllingPlayer();
    }

    @Override
    public boolean activeTick(final Level level, final LocalPlayer player) {
        if (level == null) {
            return true;
        }

        final PhysicsAssemblerBlockEntity be = PhysicsAssemblerGUIHandler.targetAssembler;
        if (be == null) {
            return true;
        }

        if (BlockHoldInteraction.inInteractionRange(player, this.getInteractionPos().getCenter(), 2)) {
            lastAnimatedValue = animatedValue;
            animatedValue += animatedVelocity;
            animatedVelocity *= 0.8f;

            be.updateControlledByPlayer(animatedValue * 45.0f);
            return false;
        }

        final boolean inPlot = be.findOwningSubLevel() != null; // [第19轮] 双端反查
        be.visualAngle.setValue(animatedValue * 45.0);
        be.clientFlickLeverTo(inPlot);
        be.stopControllingPlayer();
        return true;
    }

    @Override
    public boolean activeOnMouseMove(final double yaw, final double pitch) {
        final double scalar = 0.5 - Math.abs(0.5 - animatedValue) + 0.05;

        if (PhysicsAssemblerGUIHandler.targetAssembler == null) {
            return false;
        }
        // [1.20.1 移植修正] 1.20.1 下按住右键时，传给 turn 的垂直增量（pitch）经常为 0，
        // 导致拉杆角度 animatedValue 永远不变、释放时到不了阈值 → 拆装不触发。
        // 这里优先用垂直拖拽 pitch（拉杆本应响应的方向），pitch 为 0 时回退用 -yaw，
        // 既覆盖「只水平拖」也覆盖「参数顺序错位」两种可能。
        final double drag = pitch != 0.0 ? pitch : -yaw;

        animatedValue -= (float) ((drag / 80.0) * scalar);

        if (animatedValue > 1.0) {
            animatedValue = 1.0f;
        } else if (animatedValue < 0.0) {
            animatedValue = 0.0f;
            animatedVelocity = 0.0f;
        }

        final int signal = Math.round(animatedValue * 4.0f);

        if (signal != lastSignal) {
            lastSignal = signal;
            if (signal == 0.0f || signal == 4.0f)
                SimSoundEvents.ASSEMBLER_SHIFT.playAt(Minecraft.getInstance().level, this.getInteractionPos(), 0.5f, 0.8f + animatedValue * 0.3f, false);
            else
                SimSoundEvents.ASSEMBLER_TICK.playAt(Minecraft.getInstance().level, this.getInteractionPos(), 0.5f, 0.8f + animatedValue * 0.3f, false);
        }

        return true;
    }

    @Override
    public void renderOverlay(final GuiGraphics graphics, final int width1, final int height1, final boolean hideGui) {
        if (hideGui)
            return;
        final PoseStack ps = graphics.pose();

        ps.pushPose();

        ps.translate(graphics.guiWidth() / 2, graphics.guiHeight() / 2, 0);

        final int height = 6 + 10 * 6 + 6;

        ps.translate(10, -height / 2, 0);

        graphics.blit(SimGUITextures.ASSEMBLER_TRACK_START.location, 0, 0, 0, 0, 14, 6, 32, 32);
        ps.translate(0, 6, 0);

        for (int c = 0; c < 6; c++) {
            graphics.blit(SimGUITextures.ASSEMBLER_TRACK_MIDDLE.location, 0, 0, 0, 7, 14, 10, 32, 32);
            ps.translate(0, 10, 0);
        }

        graphics.blit(SimGUITextures.ASSEMBLER_TRACK_END.location, 0, 0, 0, 18, 14, 6, 32, 32);

        final float value = Mth.lerp(AnimationTickHolder.getPartialTicks(), lastAnimatedValue, animatedValue);
        ps.translate(-2, -12 - (51 * value), 0);
        graphics.blit(SimGUITextures.ASSEMBLER_TRACK_MIDDLE.location, 0, 0, 14, 0, 18, 14, 32, 32);

        ps.popPose();
    }

    public ClientSubLevel getSubLevelHolding() {
        return Sable.HELPER.getContainingClient(this.getInteractionPos());
    }

    public double getFraction() {
        return animatedValue;
    }
}
