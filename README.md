# Create Aeronautics（Forge 1.20.1）— 第三方移植代码存档
# Create Aeronautics (Forge 1.20.1) — Third-Party Port Source Archive

> ⚠️ **非官方 · 第三方移植 · 未经授权**
> **Unofficial · Third-Party Port · Not Authorized**
>
> 本仓库是 Create: Aeronautics 及其配套子项目（SubLevel / Sable / Simulated / Offroad 等）
> 从**原版 NeoForge 1.21.1** **移植到 Forge 1.20.1** 的**非官方、第三方移植代码存档**。
> This repository is an **unofficial, third-party port** of Create: Aeronautics (and its sub-projects
> SubLevel / Sable / Simulated / Offroad) **ported from the original NeoForge 1.21.1 to Forge 1.20.1**.
>
> 本仓库**不属于原开发团队**，未获得官方授权，仅供学习、研究与个人使用。
> This repository is **not** part of the original team and is not officially authorized; it is for learning,
> research, and personal use only.
>
> 如原模组作者或权利方要求下架，将无条件配合。
> If the original authors or rights holders request takedown, we will comply unconditionally.

## ⚠️ 许可证与版权 / License & Copyright

- 本仓库**仅包含源代码与构建配置**，**不包含任何美术素材**（贴图、模型、音频等）。
  This repository contains **source code and build config only**; it includes **no art assets** (textures, models, audio, etc.).
- 所有美术资源（textures / models / sounds 等）的版权归**原模组及其作者**所有，并遵循原模组的各自许可证（All Rights Reserved）。
  All art assets are copyrighted by **the original mod and its authors** under their respective licenses (All Rights Reserved).
- 因原模组许可限制，本仓库**不重新分发任何素材**。运行所需素材请从原模组（或其允许的来源）获取。
  Due to the original mod's license, this repository **does not redistribute any assets**. Obtain them from the original mod.
- 代码部分在遵守上述前提与原模组 MIT 许可的前提下可参考与学习；商业使用或再分发请先取得原模组作者授权。
  The code may be studied under the original mod's MIT License and the terms above; for commercial use or redistribution, obtain the original authors' permission first.

完整许可文本与第三方依赖清单见 **[LICENSE](./LICENSE)**。
The full license text and third-party dependency list are in **[LICENSE](./LICENSE)**.

## 内容范围 / Scope

✅ **上传内容 / Included**
- Java 源码、Mixin 配置（mixins.json / accesswidener）
  Java source, Mixin configs (mixins.json / accesswidener)
- `mods.toml` / `pack.mcmeta` / `architectury.common.json` 等必需配置
  Required config files
- Gradle 构建脚本（`build.gradle` / `settings.gradle` / `gradle.properties` / `build_mod.bat`）
  Gradle build scripts
- 第三方 Java 依赖（`libs/`，**独立于官方**，版权归各自作者）：
  Third-party Java dependencies (`libs/`, **separate from the official mod**, copyrighted by their authors):
  - Create `1.20.1-6.0.8`（MIT，simibubi）
  - Flywheel `1.20.1-1.0.5`（MIT，jozufozu）
  - Ponder `1.20.1-1.0.91`（MIT，simibubi / jozufozu）
  - Registrate `MC1.20-1.3.3`（MIT，tterrag1098）
  - Shoulder Surfing `1.20.1-5.0.4`（MIT，Exopandora）
  - MixinExtras `0.4.1` 与 MixinExtras-Forge `0.4.1`（MIT，LlamaLad7）
- 原生库（`src/main/resources/natives/sable_rapier/`，**独立于官方**）：
  Native libraries (separate from the official mod):
  - Rapier 物理引擎（桌面端 `sable_rapier_binaries.zip.l4z` + 安卓端 `libsable_rapier.so`，Apache-2.0，Dimforge）
  - 完整库名 / 版本 / 许可对照见 `LICENSE` 第三节。 / Full list in Section 3 of `LICENSE`.

❌ **不包含内容 / Excluded**
- 贴图（`*.png`）、音频（`*.ogg`）、3D 模型（`*.obj` / `*.bbmodel` / `*.fbx`）
  Textures, audio, 3D models
- 模型定义（`assets/*/models/**` 下的 `*.json`）
  Model definitions
- 构建产物（`build/`）、本地运行环境（`run/`）、Gradle 缓存（`.gradle/`）
  Build output, run dir, Gradle cache
- 调试 / 临时文件（`_debug/`、各种 `_*.txt` / `_*.py` 等）
  Debug / temp files

## 构建方式 / Build

1. 需要 Forge 1.20.1 开发环境及对应 JDK（17）。
   Requires a Forge 1.20.1 dev environment and JDK 17.
2. 第三方依赖已置于 `libs/`，原生库已置于 `natives/`，无需另行下载。
   Third-party deps are in `libs/`, native libs in `natives/`; no extra download needed.
3. 双击 `build_mod.bat`（或执行 Gradle `build`）进行构建，产物位于 `build/libs/`。
   Run `build_mod.bat` (or Gradle `build`); output is in `build/libs/`.
   - 注：`build_mod.bat` 为作者本地构建脚本，不同环境可能需要微调。
     Note: `build_mod.bat` is the author's local build script and may need tweaks per environment.

## ⚠️ 稳定性提示 / Stability Notice

> **本移植版可能不如原版稳定。** 它是社区个人从 NeoForge 1.21.1 手动移植到 Forge 1.20.1 的产物，
> **未经原开发团队测试**，可能含有原版中不存在的 bug、崩溃或存档损坏风险。请务必备份存档，风险自担。
>
> **This port may be LESS STABLE than the original.** Manually ported by an individual, **not tested by the
> original developers**; it may contain bugs/crashes not present in the original. Always back up your saves; use at your own risk.

## 免责声明 / Disclaimer

本仓库与 Mojang / Microsoft / Create 团队 / 原模组作者**无任何关联**。
使用本仓库产生的任何后果由使用者自行承担。
This repository is **not affiliated** with Mojang / Microsoft / the Create team / the original authors.
Any consequences of using this repository are the user's own responsibility.
