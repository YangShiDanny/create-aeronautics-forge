package dev.ryanhcode.sable.mixin.entity.entity_tracking;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public class TrackedEntityMixin {

    // [1.20.1 结构图解隐形根因修复] @Redirect 是标准注解、目标是原版类，必须走 refmap；
    // 此前误加 remap=false，生产环境（SRG 名 m_140497_/m_20182_）按字面名 updatePlayer/position 找不到注入点，
    // 且 mixins.json 未设 injectors.defaultRequire（默认 0）-> 注入静默失效。
    // 失效后服务端用子关卡实体的真实坐标（约 2048 万格）判定跟踪距离 -> 一律视为超出范围 ->
    // 给客户端发实体移除包 -> 图解等子关卡内实体在客户端消失（看不见、无法互动）。
    @Redirect(method = "updatePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 sable$trackSubLevelEntities(final Entity instance) {
        final Vec3 pos = instance.position();

        final SubLevel subLevel = Sable.HELPER.getContaining(instance.level(), pos);

        if (subLevel != null) {
            return subLevel.logicalPose().transformPosition(pos);
        } else {
            return instance.position();
        }
    }
}
