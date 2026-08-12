package dev.ryanhcode.offroad.index;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.ryanhcode.offroad.Offroad;

public class OffroadPartialModels {

	public static final PartialModel
			DIODE_LEFT = block("wheel_mount/diode_left"),
			DIODE_RIGHT = block("wheel_mount/diode_right"),
			TELE_OUTER = block("wheel_mount/tele_outer"),
			TELE_INNER = block("wheel_mount/tele_inner"),
			TELE_MOUNT = block("wheel_mount/mount"),
			SPRING_UPPER = block("wheel_mount/spring_upper"),
			SPRING_MIDDLE = block("wheel_mount/spring_middle"),
			SPRING_LOWER = block("wheel_mount/spring_lower"),
			ROCK_CUTTING_WHEEL_WHEEL = block("rockcutting_wheel/wheel"),
			// [1.20.1 紫黑贴图修复] 四种车轮模型必须在【类初始化阶段】就通过 PartialModel.of 进入
			// flywheel 的 ALL 缓存，才能被启动期的 onRegisterAdditional(注册额外模型) 与
			// onBakingCompleted(填充 bakedModel) 两个事件覆盖并完成烘焙。原先只在
			// WheelMountRenderer 运行时才 PartialModel.of(tireLike.model())，错过烘焙窗口
			// → bakedModel 为 null → 渲染成紫黑 missing texture。这里静态注册后，渲染器里
			// 同一 location 的 PartialModel.of 会经 computeIfAbsent 返回已烘焙的同一实例。
			// 说明：TireLike 里车轮 model 指向 offroad:item/<tire>/block，故用 item() 助手。
			SMALL_TIRE = item("small_tire/block"),
			TIRE = item("tire/block"),
			LARGE_TIRE = item("large_tire/block"),
			MONSTROUS_TIRE = item("monstrous_tire/block");

	private static PartialModel block(final String path) {
		return PartialModel.of(Offroad.path("block/" + path));
	}
	private static PartialModel entity(final String path) {
		return PartialModel.of(Offroad.path("entity/" + path));
	}
	private static PartialModel item(final String path) {
		return PartialModel.of(Offroad.path("item/" + path));
	}

	public static void init() {
	}
}
