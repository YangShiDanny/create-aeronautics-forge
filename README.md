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

# Create‑Aeronautics‑Forge
这是 **Create: Aeronautics** 的非官方Forge 1.20.1移植版本。

> ⚠️ Copyright Notice
> This repository contains **both Java source code and original mod assets(textures, models, sounds, lang json)** from the original Create:Aeronautics mod.
> All graphical/audio assets belong to the original author.
> This project is for **personal learning & research purpose only**, NOT for commercial usage.
> If you are the original author and believe this repository violates your copyright, please open an issue to contact me for removal.

本仓库同时包含模组Java源码以及原版mod美术资源（贴图、模型、音效、语言文件）。
全部美术资源版权归原mod作者所有。
本项目仅用于个人学习研究，禁止用于商业用途。
如果您是原作者，认为本仓库侵犯您的版权，请提交issue联系我，我会配合处理。

## Important
- This is an unofficial port, NOT endorsed by original Create:Aeronautics author.
- Do not redistribute built mod jars from this repository publicly.
- GitHub CI builds are for compile‑verification only.

## 构建说明
1. Clone本仓库
2. 使用Gradle构建
> CI编译产出仅用于验证代码编译正确性，不代表正式发布版本。请勿直接公开分发从本仓库构建出的完整mod jar。

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
- 贴图（`*.png`）、音频（`*.ogg`）、3D 模型（`*.obj` / `*.bbmodel` / `*.fbx`）
  Textures, audio, 3D models
- 模型定义（`assets/*/models/**` 下的 `*.json`）
  Model definitions

❌ **不包含内容 / Excluded**

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
