package dev.eriksonn.aeronautics.mixin.levitite;

import dev.eriksonn.aeronautics.content.components.Levitating;
import dev.eriksonn.aeronautics.index.AeroDataComponents;
import dev.simulated_team.simulated.libs.minecraft.core.component.DataComponentType;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin extends Entity {
    public ItemEntityMixin(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    @Shadow public abstract ItemStack getItem();

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;isNoGravity()Z"))
    private boolean aeronautics$levitatingGravity(final ItemEntity self) {
        // [1.20.1 移植修正] 原版基于 1.21 新增的 ItemEntity.getDefaultGravity()（返回 0 以悬浮）实现，
        // 但 1.20.1 无此方法，@Inject 找不到目标而崩（Critical injection failure: getDefaultGravity）。
        // 1.20.1 的 ItemEntity.tick() 里重力是硬编码：
        //   if (!this.isNoGravity()) setDeltaMovement(getDeltaMovement().add(0, -0.04, 0));
        // 故改为重定向那次 isNoGravity() 调用：带 Levitating 组件时返回 true（视为无重力、跳过 -0.04 重力施加），
        // 语义等价于原 getDefaultGravity 返回 0。
        final Levitating component = DataComponentType.get(this.getItem(), AeroDataComponents.LEVITATING);
        if (component != null) {
            return true;
        }
        return self.isNoGravity();
    }

    @Inject(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))
    private void aeronautics$levitatingDragAndSparkles(final CallbackInfo ci) {
        final Levitating component = DataComponentType.get(this.getItem(), AeroDataComponents.LEVITATING);
        if (component != null) {
            final float dragFraction = Mth.clamp(component.dragFraction(), 0, 1);
            this.setDeltaMovement(this.getDeltaMovement().scale(dragFraction));

            if (this.level().isClientSide && component.particle().isPresent()) {
                if (this.level().random.nextFloat() < Mth.clamp(this.getItem().getCount() - 10, 5, 100) / 64f) {
                    final Vec3 ppos = VecHelper.offsetRandomly(this.getPosition(0), this.random, 0.4f).add(0, 0.3, 0);
                    this.level().addParticle(component.particle().get(), ppos.x, ppos.y, ppos.z, 0, 0, 0);
                }
            }
        }
    }
}
