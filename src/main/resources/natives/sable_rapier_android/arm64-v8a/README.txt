把用 sable_rapier_android 工程（Android NDK + cargo-ndk 交叉编译）产出的 libsable_rapier.so
放到本目录，文件名必须保持为 libsable_rapier.so，然后重新构建模组 jar 即可被手机加载。

本目录对应安卓 ABI：arm64-v8a
适用：64 位安卓手机（绝大多数现代手机，如骁龙 / 天玑 / 麒麟的 64 位机型）
