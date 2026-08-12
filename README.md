# Create Aeronautics（Forge 1.20.1）— 第三方移植代码存档

> ⚠️ **非官方 · 第三方移植 · 未经授权**
>
> 本仓库是 Create: Aeronautics 及其配套子项目（SubLevel / Sable / Simulated / Offroad 等）
> 从 **NeoForge 1.21.1** 向 **Forge 1.20.1** 的**非官方、第三方移植代码存档**。
> 本仓库**不属于原开发团队**，未获得官方授权，仅供学习、研究与个人使用。
> 如原模组作者或权利方要求下架，将无条件配合。

## ⚠️ 许可证与版权

- 本仓库**仅包含源代码与构建配置**，**不包含任何美术素材**（贴图、模型、音频等）。
- 所有美术资源（textures / models / sounds 等）的版权归**原模组及其作者**所有，
  并遵循原模组的各自许可证（All Rights Reserved / 原仓库许可）。
- 因原模组许可限制，本仓库**不重新分发任何素材**。运行所需素材请从原模组（或其允许的来源）获取，
  或将原模组资源放置于对应资源路径后再构建。
- 源码部分：在遵守上述前提与原模组许可的前提下，可参考与学习；
  商业使用或再分发请先取得原模组作者授权。

## 内容范围

✅ **上传内容**
- Java 源码、Mixin 配置（mixins.json / accesswidener）
- `mods.toml` / `pack.mcmeta` / `architectury.common.json` 等必需配置
- Gradle 构建脚本（`build.gradle` / `settings.gradle` / `gradle.properties` / `build_mod.bat`）
- 第三方依赖（`libs/`，含 Create / Flywheel / Ponder / Registrate 等）
- 原生库（`natives/`，含 Rapier 安卓原生库等）

❌ **不包含内容**
- 贴图（`*.png`）、音频（`*.ogg`）、3D 模型（`*.obj` / `*.bbmodel` / `*.fbx`）
- 模型定义（`assets/*/models/**` 下的 `*.json`）
- 构建产物（`build/`）、本地运行环境（`run/`）、Gradle 缓存（`.gradle/`）
- 调试 / 临时文件（`_debug/`、各种 `_*.txt` / `_*.py` 等）

## 构建方式

1. 需要 Forge 1.20.1 开发环境及对应 JDK（17）。
2. 第三方依赖已置于 `libs/`，原生库已置于 `natives/`，无需另行下载。
3. 双击 `build_mod.bat`（或执行 Gradle `build`）进行构建，产物位于 `build/libs/`。
   - 注：`build_mod.bat` 为作者本地构建脚本，不同环境可能需要微调。

## 免责声明

本仓库与 Mojang / Microsoft / Create 团队 / 原模组作者**无任何关联**。
使用本仓库产生的任何后果由使用者自行承担。
