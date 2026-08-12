package dev.ryanhcode.sable.physics.impl.rapier;

import dev.ryanhcode.sable.MobilePlatform;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderData;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.Util.OS;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix3dc;
import org.joml.Vector3dc;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Java side of the sable_rapier bridge for using the Rapier 3D physics engine.
 * This is purely for internal use. Use {@link dev.ryanhcode.sable.api.physics.PhysicsPipeline} for interacting with the physics engine.
 */
@ApiStatus.Internal
public final class Rapier3D {
    private static final String NATIVE_DIR = ".sable/natives";
    private static final String LIB_ZIP_NAME = "sable_rapier_binaries.zip.l4z";
    private static final String LIB_NAME = "sable_rapier";

    // [1.20.1 移植·安卓] 安卓原生库单独资源目录：由本机用 sable_rapier_android 工程
    // (Android NDK + cargo-ndk 交叉编译) 产出 libsable_rapier.so，按 ABI 分目录塞进 jar，
    // 不再依赖桌面版 sable_rapier_binaries.zip.l4z（其中不含安卓架构库，导致安卓启动即崩）。
    private static final String ANDROID_NATIVE_RES = "/natives/sable_rapier_android";
    private static final String ANDROID_LIB_NAME = "libsable_rapier.so";

    public static final String NATIVE_NAME = getNativeName();

    private static int countingObjectID = 0;

    static {
        loadLibrary();
    }

    private static String getNativeName() {
        final String arch;
        if (System.getProperty("os.arch").equals("arm") || System.getProperty("os.arch").startsWith("aarch64")) {
            arch = "aarch64";
        } else {
            arch = "x86_64";
        }

        final OS os = Util.getPlatform();
        if (os == OS.WINDOWS) {
            return LIB_NAME + "_" + arch + "_windows.dll";
        } else if (os == OS.OSX) {
            return LIB_NAME + "_" + arch + "_macos.dylib";
        } else {
            if (os != OS.LINUX) {
                Sable.LOGGER.error("Unknown platform '{}' detected, sable will attempt to use linux natives, this may or may not work.", System.getProperty("os.name"));
            }
            return LIB_NAME + "_" + arch + "_linux.so";
        }
    }

    private static void loadLibrary() {
        // [1.20.1 移植·安卓] 安卓端优先走自带原生库资源目录，避开桌面 l4z（其中无安卓库）
        if (isAndroid()) {
            loadAndroidLibrary();
            return;
        }
        try (final InputStream is = Rapier3D.class.getResourceAsStream("/natives/" + LIB_NAME + "/" + LIB_ZIP_NAME)) {
            if (is == null) {
                throw new FileNotFoundException(LIB_ZIP_NAME);
            }

            final Path dir = Paths.get(NATIVE_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            // [1.20.1 移植修正·v2] 彻底解决 Windows 下原生库 AccessDenied 崩溃：
            // 进世界时 Sable 物理系统静态初始化会加载 Rapier 原生 DLL。Windows 中一旦 DLL 被某
            // 进程 System.load 锁定（常见于：上一次游戏崩溃/被杀未释放句柄，或杀软实时扫描锁定），
            // 再次启动时若直接覆盖/删除该 DLL 都会抛 AccessDeniedException -> 物理系统初始化失败 ->
            // 服务端初始化即崩（崩溃报告 Description: Sable linking with Rapier natives）。
            // 旧版用固定文件名 + REPLACE_EXISTING 覆盖，正是崩因（被锁文件无法覆盖）。
            // 修复：每次运行解压到全局唯一文件名（含 UUID），绝不触碰可能仍被锁定的旧 DLL；
            // System.load 该唯一副本即可。启动前顺手清理历史残留的临时 DLL（被锁的删不掉则忽略）。
            // 注意：临时副本被本进程锁定，JVM 退出时无法删除，故仅尽力清理历史残留，自身残留无害。
            final String baseName = NATIVE_NAME;
            final String uniqueName = baseName.replaceFirst("(\\.[a-zA-Z0-9]+)$", "-" + UUID.randomUUID() + "$1");
            final Path nativeFile = dir.resolve(uniqueName);

            // 清理历史残留的同名临时 DLL（如 sable_rapier_x86_64_windows-<uuid>.dll），被锁则忽略
            try (final DirectoryStream<Path> ds = Files.newDirectoryStream(
                    dir, baseName.replace(".dll", "-*.dll"))) {
                for (final Path old : ds) {
                    try {
                        Files.deleteIfExists(old);
                    } catch (final Throwable ignored) {
                    }
                }
            } catch (final Throwable ignored) {
            }

            try (final LZ4FrameInputStream is2 = new LZ4FrameInputStream(is);
                 final ZipInputStream ti = new ZipInputStream(is2)) {

                ZipEntry entry;
                while ((entry = ti.getNextEntry()) != null) {
                    if (entry.getName().equals(NATIVE_NAME)) {
                        Files.copy(ti, nativeFile, StandardCopyOption.REPLACE_EXISTING);
                        // 清除 Windows 只读属性，避免 System.load 受影响
                        try {
                            nativeFile.toFile().setReadable(true);
                            nativeFile.toFile().setWritable(true);
                        } catch (final Throwable ignored) {
                        }
                        System.load(nativeFile.toAbsolutePath().toString());
                        return;
                    }
                }

                throw new FileNotFoundException(NATIVE_NAME);
            }
        } catch (final Throwable t) {
            Sable.LOGGER.error(
                    "Sable has failed to load the natives needed for its Rapier pipeline. Please report with system details and logs to {}",
                    Sable.ISSUE_TRACKER_URL,
                    t);
            // UnsatisfiedLinkError 的 getCause() 常为 null，直接传给 CrashReport 会导致
            // 生成报告时二次抛 NPE（this.f_127501_ 为 null），真实错误被完全掩盖。
            final Throwable reported = t.getCause() != null ? t.getCause() : t;
            final CrashReport crashReport = CrashReport.forThrowable(reported, "Sable linking with Rapier natives");
            final CrashReportCategory category = crashReport.addCategory("Natives");
            category.setDetail("Name", Rapier3D.NATIVE_NAME);
            throw new ReportedException(crashReport);
        }
    }

    // ===== 以下为 [1.20.1 移植·安卓] 新增：安卓原生库独立加载 =====

    private static boolean isAndroid() {
        // [1.20.1 移植·安卓] 统一委托 MobilePlatform 做安卓判定，避免多份检测逻辑分叉。
        return MobilePlatform.isAndroid();
    }

    private static String getAndroidAbi() {
        final String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (arch.startsWith("aarch64")) {
            return "arm64-v8a";
        } else if (arch.equals("arm")) {
            return "armeabi-v7a";
        } else if (arch.contains("x86_64")) {
            return "x86_64";
        } else if (arch.contains("x86") || arch.equals("i386") || arch.equals("i686")) {
            return "x86";
        }
        return null;
    }

    /**
     * [1.20.1 移植·安卓] 从路径里提取出应用私有根目录，形如 {@code /data/data/<包名>}
     * 或 {@code /data/user/<用户号>/<包名>}；提取不到返回 null。
     * <p>
     * 注意：这里刻意用纯字符串解析而非预编译的静态 {@code java.util.regex.Pattern} 常量。
     * 本类的 {@code static { loadLibrary(); }} 位于类顶部，而 Java 的静态初始化
     * 严格按源码文本顺序执行——任何声明在 static 块之后的静态字段，在 static 块
     * 运行期间都还是 null。曾因此在安卓端抛
     * {@code NullPointerException: ... ANDROID_APP_ROOT is null}，务必不要再改回静态常量。
     */
    private static Path extractAndroidAppRoot(final String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return null;
        }
        final String path = rawPath.replace('\\', '/');
        // 必须从根开始，避免 /storage/emulated/0/..../data/data/xxx 这类假私有路径被误判
        if (!path.startsWith("/data/")) {
            return null;
        }

        final String[] seg = path.split("/");
        // 形如 ["", "data", "data", "<包名>", ...] 或 ["", "data", "user", "<数字>", "<包名>", ...]
        if (seg.length < 4 || !"data".equals(seg[1])) {
            return null;
        }

        final int pkgIndex;
        if ("data".equals(seg[2])) {
            pkgIndex = 3;
        } else if ("user".equals(seg[2])) {
            if (seg.length < 5 || !isAllDigits(seg[3])) {
                return null;
            }
            pkgIndex = 4;
        } else {
            return null;
        }

        if (!isAndroidPackageName(seg[pkgIndex])) {
            return null;
        }

        final StringBuilder root = new StringBuilder();
        for (int i = 1; i <= pkgIndex; i++) {
            root.append('/').append(seg[i]);
        }
        return Paths.get(root.toString());
    }

    private static boolean isAllDigits(final String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * 安卓包名：由 {@code .} 分隔的若干段，每段仅含字母、数字、下划线且不为空。
     */
    private static boolean isAndroidPackageName(final String s) {
        if (s == null || s.isEmpty() || s.charAt(0) == '.' || s.charAt(s.length() - 1) == '.') {
            return false;
        }
        int segLen = 0;
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c == '.') {
                if (segLen == 0) {
                    return false;
                }
                segLen = 0;
                continue;
            }
            final boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_';
            if (!ok) {
                return false;
            }
            segLen++;
        }
        return segLen > 0;
    }

    /**
     * [1.20.1 移植·安卓] 扫描 /proc/self/maps，找出 JVM 自身已成功 dlopen 的 .so。
     * <p>
     * 这类路径是「实测可加载」的铁证——既在 linker 允许的命名空间内，
     * 又不受 Android 10 起「禁止执行应用私有目录中可写文件」(W^X) 策略的阻拦。
     * 因此同时收集两样东西：
     * <ul>
     *   <li>{@code roots}：应用私有根目录（如 /data/user/0/com.tungsten.fcl）</li>
     *   <li>{@code provenDirs}：那些 .so 所在的具体目录，可靠性最高的兜底落库点</li>
     * </ul>
     */
    private static void collectFromMaps(final Collection<Path> roots, final Collection<Path> provenDirs) {
        final Path maps = Paths.get("/proc/self/maps");
        if (!Files.isReadable(maps)) {
            return;
        }
        try (final Stream<String> lines = Files.lines(maps)) {
            lines.forEach(line -> {
                final int idx = line.indexOf("/data/");
                if (idx < 0) {
                    return;
                }
                final String path = line.substring(idx).trim();
                if (!path.endsWith(".so")) {
                    return;
                }
                final Path root = extractAndroidAppRoot(path);
                if (root == null) {
                    return;
                }
                roots.add(root);
                final Path parent = Paths.get(path).getParent();
                if (parent != null) {
                    provenDirs.add(parent);
                }
            });
        } catch (final Throwable ignored) {
        }
    }

    /**
     * [1.20.1 移植·安卓] 按可靠性从高到低列出可用于 dlopen 的候选落库目录。
     * <p>
     * 安卓 7.0 起启用 linker 命名空间隔离：只有应用私有目录（/data/data/&lt;包名&gt;、
     * /data/user/&lt;n&gt;/&lt;包名&gt;）下的 .so 才允许被 dlopen。外部存储
     * （/storage/emulated/0，即 sdcard）既是 noexec 挂载又不在命名空间白名单内，
     * 从那里 System.load 必定抛 UnsatisfiedLinkError:
     * {@code ... is not accessible for the namespace "clns-N"}。
     */
    private static List<Path> androidNativeDirCandidates() {
        final LinkedHashSet<Path> roots = new LinkedHashSet<>();
        final LinkedHashSet<Path> dirs = new LinkedHashSet<>();

        // 1) java.io.tmpdir：FCL/Pojav 等启动器一律用 -Djava.io.tmpdir 指到私有目录，
        //    例如 FCL 的 /data/user/0/com.tungsten.fcl/cache/fclauncher，命中率最高。
        final String tmpDir = System.getProperty("java.io.tmpdir", "");
        final Path tmpRoot = extractAndroidAppRoot(tmpDir);
        if (tmpRoot != null) {
            dirs.add(Paths.get(tmpDir).resolve("sable_natives"));
            roots.add(tmpRoot);
        }

        // 2) java.home：JRE 本体一定位于私有目录（否则 libjvm.so 自己就加载不了）
        final Path javaHomeRoot = extractAndroidAppRoot(System.getProperty("java.home", ""));
        if (javaHomeRoot != null) {
            roots.add(javaHomeRoot);
        }

        // 3) 环境变量兜底
        for (final String key : new String[]{"TMPDIR", "HOME", "POJAV_NATIVEDIR"}) {
            try {
                final Path root = extractAndroidAppRoot(System.getenv(key));
                if (root != null) {
                    roots.add(root);
                }
            } catch (final Throwable ignored) {
            }
        }

        // 4) /proc/self/maps：JVM 自身已成功 dlopen 的 .so，实测可加载
        final LinkedHashSet<Path> provenDirs = new LinkedHashSet<>();
        collectFromMaps(roots, provenDirs);

        // 每个私有根下依次尝试这几个子目录
        for (final Path root : roots) {
            dirs.add(root.resolve("files").resolve("sable_natives"));
            dirs.add(root.resolve("cache").resolve("sable_natives"));
            dirs.add(root.resolve("code_cache").resolve("sable_natives"));
        }

        // 5) 实测可 dlopen 的目录，先试其下的子目录，再退而求其次直接写进该目录本身。
        //    这是应对 Android 10+ W^X 策略最后的保险：既然启动器的 libjvm.so 能从这里
        //    加载，我们的库放同一处也一定能加载。
        for (final Path proven : provenDirs) {
            dirs.add(proven.resolve("sable_natives"));
        }
        dirs.addAll(provenDirs);

        // 6) 最后兜底：原来的相对路径（多半在 sdcard 上会失败，但万一是自定义启动器则可用）
        dirs.add(Paths.get(NATIVE_DIR));

        return new ArrayList<>(dirs);
    }

    /**
     * [1.20.1 移植·安卓] 把原生库写入指定目录并尝试 dlopen，成功返回落地文件路径。
     */
    private static Path tryLoadFrom(final Path dir, final byte[] libBytes, final String abi) throws Exception {
        Files.createDirectories(dir);

        // 清理本目录下历史残留的同 ABI 临时库（仍被锁定的删不掉，忽略即可）
        try (final DirectoryStream<Path> ds = Files.newDirectoryStream(dir, abi + "-libsable_rapier-*.so")) {
            for (final Path old : ds) {
                try {
                    Files.deleteIfExists(old);
                } catch (final Throwable ignored) {
                }
            }
        } catch (final Throwable ignored) {
        }

        final Path nativeFile = dir.resolve(abi + "-libsable_rapier-" + UUID.randomUUID() + ".so");
        Files.write(nativeFile, libBytes);
        try {
            final java.io.File f = nativeFile.toFile();
            f.setReadable(true);
            f.setWritable(true);
            // 安卓下 dlopen 需要可执行位，桌面版原逻辑漏了这一步
            f.setExecutable(true);
        } catch (final Throwable ignored) {
        }

        boolean loaded = false;
        try {
            System.load(nativeFile.toAbsolutePath().toString());
            loaded = true;
        } finally {
            if (!loaded) {
                // 本目录不可用：立刻删掉刚写下的库文件，
                // 否则逐个候选目录试下来会在手机上堆一堆 2MB 垃圾。
                try {
                    Files.deleteIfExists(nativeFile);
                } catch (final Throwable ignored) {
                }
            }
        }
        return nativeFile;
    }

    private static void loadAndroidLibrary() {
        final String abi = getAndroidAbi();
        if (abi == null) {
            throw new UnsupportedOperationException("Unsupported Android architecture: " + System.getProperty("os.arch"));
        }
        final String resPath = ANDROID_NATIVE_RES + "/" + abi + "/" + ANDROID_LIB_NAME;

        // 先整体读进内存：候选目录要逐个重试，资源流只能消费一次
        final byte[] libBytes;
        try (final InputStream is = Rapier3D.class.getResourceAsStream(resPath)) {
            if (is == null) {
                throw new FileNotFoundException(
                        "未找到安卓原生库 " + resPath + "，请先用 sable_rapier_android 工程编译 libsable_rapier.so 并放入对应 ABI 目录后重新构建");
            }
            libBytes = is.readAllBytes();
        } catch (final Throwable t) {
            Sable.LOGGER.error("Sable 无法从模组内读取安卓原生库资源 {}。", resPath, t);
            final CrashReport crashReport = CrashReport.forThrowable(t, "Sable reading Rapier natives (android)");
            crashReport.addCategory("Natives").setDetail("Abi", abi).setDetail("Resource", resPath);
            throw new ReportedException(crashReport);
        }

        final List<Path> candidates = androidNativeDirCandidates();
        final StringBuilder failures = new StringBuilder();
        Throwable lastError = null;

        for (final Path dir : candidates) {
            try {
                final Path loaded = tryLoadFrom(dir, libBytes, abi);
                Sable.LOGGER.info("Sable 已在安卓端成功加载 Rapier 原生库：{}", loaded.toAbsolutePath());
                return;
            } catch (final Throwable t) {
                lastError = t;
                failures.append("\n  - ").append(dir).append(" => ").append(t.getClass().getSimpleName())
                        .append(": ").append(t.getMessage());
            }
        }

        Sable.LOGGER.error(
                "Sable 在安卓端加载 Rapier 原生库失败（ABI={}）。已尝试过的全部目录：{}\n"
                        + "提示：安卓只允许从应用私有目录（/data/data/<包名> 或 /data/user/<n>/<包名>）加载 .so，"
                        + "外部存储 /storage/emulated/0 会被 linker 命名空间拒绝。",
                abi, failures);

        // 注意：UnsatisfiedLinkError 的 getCause() 恒为 null，这里绝不能直接把 cause 交给 CrashReport，
        // 否则 CrashReport 内部字段为 null，生成报告时会二次抛 NPE，把真实错误完全掩盖掉。
        final Throwable reported = lastError == null
                ? new IllegalStateException("没有任何可用的原生库落库目录")
                : lastError;
        final CrashReport crashReport = CrashReport.forThrowable(reported, "Sable linking with Rapier natives (android)");
        final CrashReportCategory category = crashReport.addCategory("Natives");
        category.setDetail("Abi", abi);
        category.setDetail("Resource", resPath);
        category.setDetail("Tried directories", failures.toString());
        throw new ReportedException(crashReport);
    }

    /**
     * Retrieves the body ID for a given server sub-level
     *
     * @return the ID
     */
    @ApiStatus.Internal
    public static int getID(final PhysicsPipelineBody body) {
        return body.getRuntimeId();
    }

    @ApiStatus.Internal
    public static synchronized int nextBodyID() {
        return countingObjectID++;
    }

    @ApiStatus.Internal
    public static long getSceneHandle(final ServerLevel level) {
        final PhysicsPipeline pipeline = SubLevelPhysicsSystem.require(level).getPipeline();

        if (!(pipeline instanceof final RapierPhysicsPipeline rapierPipeline)) {
            throw new IllegalStateException("ServerLevel does not use the Rapier physics pipeline");
        }

        return rapierPipeline.getSceneHandle();
    }

    @ApiStatus.Internal
    static native long initialize(double gravityX, double gravityY, double gravityZ, double universalDrag);

    @ApiStatus.Internal
    static native void tick(final long sceneHandle, double timeStep);

    @ApiStatus.Internal
    static native void step(final long sceneHandle, double timeStep);

    /**
     * All poses are formatted in a double array as:
     * [x, y, z, qx, qy, qz, qw]
     */

    @ApiStatus.Internal
    static native void createSubLevel(final long sceneHandle, int id, double[] pose);

    /**
     * Removes an object from the physics world.
     */
    @ApiStatus.Internal
    static native void removeSubLevel(final long sceneHandle, int id);

    /**
     * All poses are formatted in a double array as:
     * [x, y, z, qx, qy, qz, qw]
     */
    @ApiStatus.Internal
    public static native void createBox(final long sceneHandle, int id, double mass, double halfExtentsX, double halfExtentsY, double halfExtentsZ, double[] pose);

    /**
     * All poses are formatted in a double array as:
     * [x, y, z, qx, qy, qz, qw]
     */
    @ApiStatus.Internal
    public static native void removeBox(final long sceneHandle, int id);

    /**
     * Gets the pose of an object.
     *
     * @param id    the object ID
     * @param store The array to store pose of the object in the format [x, y, z, qx, qy, qz, qw]
     */
    @ApiStatus.Internal
    public static native void getPose(final long sceneHandle, int id, double[] store);

    /**
     * Sets the center of mass in block coordinates.
     *
     * @param id the object ID
     * @param x  the x position of the center of mass
     * @param y  the y position of the center of mass
     * @param z  the z position of the center of mass
     */
    @ApiStatus.Internal
    static native void setCenterOfMass(final long sceneHandle, int id, double x, double y, double z);

    /**
     * Sets the local block bounds of an object.
     *
     * @param id   the object ID
     * @param minX the minimum x bound (inclusive)
     * @param minY the minimum y bound (inclusive)
     * @param minZ the minimum z bound (inclusive)
     * @param maxX the maximum x bound (inclusive)
     * @param maxY the maximum y bound (inclusive)
     * @param maxZ the maximum z bound (inclusive)
     */
    @ApiStatus.Internal
    static native void setLocalBounds(final long sceneHandle, int id, int minX, int minY, int minZ, int maxX, int maxY, int maxZ);

    /**
     * Sets a chunk at given chunk coordinates.
     *
     * @param x      the chunk x coordinate
     * @param y      the chunk y coordinate
     * @param z      the chunk z coordinate
     * @param chunk  a 4096-long (16x16x16) integer array     stored in xzy order, with x fastest changing.
     * @param global if the chunk is a part of the global world
     * @param id     the object ID the chunk is in, if not global
     */
    @ApiStatus.Internal
    static native void addChunk(final long sceneHandle, int x, int y, int z, int[] chunk, boolean global, int id);

    /**
     * Removes a chunk at given chunk coordinates.
     *
     * @param x      the chunk x coordinate
     * @param y      the chunk y coordinate
     * @param z      the chunk z coordinate
     * @param global if the chunk is a part of the global world
     */
    @ApiStatus.Internal
    static native void removeChunk(final long sceneHandle, int x, int y, int z, boolean global);

    /**
     * Sets a block if it is inside a tracked chunk.
     *
     * @param x        the block x coordinate
     * @param y        the block y coordinate
     * @param z        the block z coordinate
     * @param newState the new physics block ID + 1 of the block, or 0 for empty
     */
    @ApiStatus.Internal
    public static native void changeBlock(final long sceneHandle, int x, int y, int z, int newState);

    /**
     * Adds a new voxel collider data entry.
     *
     * @param frictionMultiplier the friction multiplier
     * @param isFluid            if the block should be treated as a fluid
     * @param contactEvents      if the block has special contact event behavior
     * @return the ID of the new block collider data entry
     */
    @ApiStatus.Internal
    private static native int newVoxelCollider(double frictionMultiplier, double volume, double restitution, boolean isFluid, BlockSubLevelCollisionCallback contactEvents);

    /**
     * Adds a new box to a voxel collider data entry.
     *
     * @param index  the ID of the block physics data entry from {@link Rapier3D#newVoxelCollider(double, double, double, boolean, BlockSubLevelCollisionCallback)}}
     * @param bounds a 6-long double array, formatted [minX, minY, minZ, maxX, maxY, maxZ]
     */
    @ApiStatus.Internal
    public static native void addVoxelColliderBox(int index, double[] bounds);

    /**
     * Clears all boxes from a voxel collider data entry.
     *
     * @param index the ID of the block physics data entry from {@link Rapier3D#newVoxelCollider(double, double, double, boolean, BlockSubLevelCollisionCallback)}}
     */
    @ApiStatus.Internal
    public static native void clearVoxelColliderBoxes(int index);

    /**
     * Sets the mass, center of mass, and inertia tensor of a block physics data entry.
     *
     * @param index the ID of the physics object
     */
    @ApiStatus.Internal
    private static native void setMassProperties(final long sceneHandle, int index, double mass, double[] centerOfMass, double[] inertiaTensor);

    /**
     * Allocates a new block physics data entry
     *
     * @param frictionMultiplier the friction multiplier
     * @param isFluid            if the block should be treated as a fluid
     * @param contactEvents      if the block has special contact event behavior
     * @return the handle of the new block physics data entry
     */
    @ApiStatus.Internal
    public static RapierVoxelColliderData createVoxelColliderEntry(final double frictionMultiplier, final double volume, final double restitution, final boolean isFluid, final BlockSubLevelCollisionCallback contactEvents) {
        return new RapierVoxelColliderData(Rapier3D.newVoxelCollider(frictionMultiplier, volume, restitution, isFluid, contactEvents));
    }

    /**
     * Teleports an object to a new position.
     *
     * @param id the object ID
     * @param x  the new x position
     * @param y  the new y position
     * @param z  the new z position
     */
    @ApiStatus.Internal
    static native void teleportObject(final long sceneHandle, int id, double x, double y, double z, double i, double j, double k, double r);

    /**
     * "Wakes up" an object, indicating environmental or other changes have occurred that should resume physics if idled or sleeping
     *
     * @param id the object ID
     */
    @ApiStatus.Internal
    public static native void wakeUpObject(final long sceneHandle, int id);

    /**
     * Adds a rotational constraint between two objects.
     *
     * @param id            the object ID
     * @param otherId       the other object ID
     * @param localAnchorXA the local anchor X on the first object
     * @param localAnchorYA the local anchor Y on the first object
     * @param localAnchorZA the local anchor Z on the first object
     * @param localAnchorXB the local anchor X on the second object
     * @param localAnchorYB the local anchor Y on the second object
     * @param localAnchorZB the local anchor Z on the second object
     * @param localAxisXA   the local axis X on the first object
     * @param localAxisYA   the local axis Y on the first object
     * @param localAxisZA   the local axis Z on the first object
     * @param localAxisXB   the local axis X on the second object
     * @param localAxisYB   the local axis Y on the second object
     * @param localAxisZB   the local axis Z on the second object
     */
    @ApiStatus.Internal
    public static native long addRotaryConstraint(final long sceneHandle,
                                                  int id,
                                                  int otherId,
                                                  double localAnchorXA,
                                                  double localAnchorYA,
                                                  double localAnchorZA,
                                                  double localAnchorXB,
                                                  double localAnchorYB,
                                                  double localAnchorZB,
                                                  double localAxisXA,
                                                  double localAxisYA,
                                                  double localAxisZA,
                                                  double localAxisXB,
                                                  double localAxisYB,
                                                  double localAxisZB);

    /**
     * Adds a fixed constraint between two objects.
     *
     * @param id                 the object ID
     * @param otherId            the other object ID
     * @param localAnchorXA      the local anchor X on the first object
     * @param localAnchorYA      the local anchor Y on the first object
     * @param localAnchorZA      the local anchor Z on the first object
     * @param localAnchorXB      the local anchor X on the second object
     * @param localAnchorYB      the local anchor Y on the second object
     * @param localAnchorZB      the local anchor Z on the second object
     * @param localOrientationXB the local orientation X of the second object relative to the first
     * @param localOrientationYB the local orientation Y of the second object relative to the first
     * @param localOrientationZB the local orientation Z of the second object relative to the first
     * @param localOrientationWB the local orientation W of the second object relative to the first
     */
    @ApiStatus.Internal
    public static native long addFixedConstraint(final long sceneHandle,
                                                 int id,
                                                 int otherId,
                                                 double localAnchorXA,
                                                 double localAnchorYA,
                                                 double localAnchorZA,
                                                 double localAnchorXB,
                                                 double localAnchorYB,
                                                 double localAnchorZB,
                                                 double localOrientationXB,
                                                 double localOrientationYB,
                                                 double localOrientationZB,
                                                 double localOrientationWB);

    /**
     * Adds a free constraint between two objects.
     *
     * @param id      the object ID
     * @param otherId the other object ID
     */
    @ApiStatus.Internal
    public static native long addFreeConstraint(final long sceneHandle,
                                                int id,
                                                int otherId,
                                                double localAnchorXA,
                                                double localAnchorYA,
                                                double localAnchorZA,
                                                double localAnchorXB,
                                                double localAnchorYB,
                                                double localAnchorZB,
                                                double localOrientationXB,
                                                double localOrientationYB,
                                                double localOrientationZB,
                                                double localOrientationWB);

    /**
     * Adds a generic constraint between two objects.
     *
     * @param id                 the object ID
     * @param otherId            the other object ID
     * @param localAnchorXA      the local anchor X on the first object
     * @param localAnchorYA      the local anchor Y on the first object
     * @param localAnchorZA      the local anchor Z on the first object
     * @param localOrientationXA the local orientation X of the first object
     * @param localOrientationYA the local orientation Y of the first object
     * @param localOrientationZA the local orientation Z of the first object
     * @param localOrientationWA the local orientation W of the first object
     * @param localAnchorXB      the local anchor X on the second object
     * @param localAnchorYB      the local anchor Y on the second object
     * @param localAnchorZB      the local anchor Z on the second object
     * @param localOrientationXB the local orientation X of the second object
     * @param localOrientationYB the local orientation Y of the second object
     * @param localOrientationZB the local orientation Z of the second object
     * @param localOrientationWB the local orientation W of the second object
     * @param lockedAxesMask     bit mask of locked axes; bit {@code n} corresponds to {@link dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis#ordinal()}
     */
    @ApiStatus.Internal
    public static native long addGenericConstraint(final long sceneHandle,
                                                   int id,
                                                   int otherId,
                                                   double localAnchorXA,
                                                   double localAnchorYA,
                                                   double localAnchorZA,
                                                   double localOrientationXA,
                                                   double localOrientationYA,
                                                   double localOrientationZA,
                                                   double localOrientationWA,
                                                   double localAnchorXB,
                                                   double localAnchorYB,
                                                   double localAnchorZB,
                                                   double localOrientationXB,
                                                   double localOrientationYB,
                                                   double localOrientationZB,
                                                   double localOrientationWB,
                                                   int lockedAxesMask);

    /**
     * Sets the local frame on one side of a constraint.
     *
     * @param handle the handle of the constraint
     * @param side   {@code 0} for the first body, {@code 1} for the second body
     */
    @ApiStatus.Internal
    public static native void setConstraintFrame(final long sceneHandle, long handle, int side, double localPosX, double localPosY, double localPosZ, double localOrientationX, double localOrientationY, double localOrientationZ, double localOrientationW);

    /**
     * Sets if contacts are enabled between the two bodies in the constraint
     *
     * @param handle the handle of the constraint
     */
    @ApiStatus.Internal
    public static native void setConstraintContactsEnabled(final long sceneHandle, long handle, boolean contactsEnabled);

    /**
     * Gets the latest joint impulses
     *
     * @param handle the handle of the constraint
     */
    @ApiStatus.Internal
    public static native void getConstraintImpulses(final long sceneHandle, long handle, final double[] store);

    /**
     * Checks if a constraint is valid
     *
     * @param handle the handle of the constraint
     */
    @ApiStatus.Internal
    public static native boolean isConstraintValid(final long sceneHandle, long handle);

    /**
     * Removes a constraint with a handle
     *
     * @param handle the handle of the constraint
     */
    @ApiStatus.Internal
    public static native void removeConstraint(final long sceneHandle, long handle);


    /**
     * Sets a constraint motor, with a desired angle and PD controller coefficients.
     */
    @ApiStatus.Internal
    public static native void setConstraintMotor(final long sceneHandle, long handle, int axis, double desiredPosition, double stiffness, double damping, boolean hasForceLimit, double maxForce);

    /**
     * Sets a constraint limit, with an axis and min/max
     */
    @ApiStatus.Internal
    public static native void setConstraintLimit(final long sceneHandle, long handle, int axis, double min, double max);

    /**
     * Locks the given constraint axes on a constraint
     */
    @ApiStatus.Internal
    public static native void lockConstraintAxes(final long sceneHandle, long handle, byte mask);

    /**
     * Adds linear and angular velocities
     *
     * @param bodyId   the ID of an already created rigid-body
     * @param linearX  x component of the linear velocity to add [m/s]
     * @param linearY  y component of the linear velocity to add [m/s]
     * @param linearZ  z component of the linear velocity to add [m/s]
     * @param angularX x component of the angular velocity to add [rad/s]
     * @param angularY y component of the angular velocity to add [rad/s]
     * @param angularZ z component of the angular velocity to add [rad/s]
     */
    @ApiStatus.Internal
    public static native void addLinearAngularVelocities(final long sceneHandle, int bodyId, double linearX, double linearY, double linearZ, double angularX, double angularY, double angularZ, final boolean wakeUp);

    /**
     * Reads & clears all reported collisions from the physics engine.
     * <p>
     * Each collision is formatted as:
     * [body_a, body_b, force_amount, local_normal_a, local_normal_b, local_point_a, local_point_b]
     */
    @ApiStatus.Internal
    static native double[] clearCollisions(long sceneHandle);

    /**
     * Applies a force to a given body
     *
     * @param bodyID the ID of an already created rigid-body
     * @param x      the x position of the force relative to the center of mass
     * @param y      the y position of the force relative to the center of mass
     * @param z      the z position of the force relative to the center of mass
     * @param fx     the x component of the force to apply [N]
     * @param fy     the y component of the force to apply [N]
     * @param fz     the z component of the force to apply [N]
     */
    @ApiStatus.Internal
    static native void applyForce(final long sceneHandle, final int bodyID, final double x, final double y, final double z, final double fx, final double fy, final double fz, final boolean wakeUp);

    /**
     * Applies a force to a given body
     *
     * @param bodyID the ID of an already created rigid-body
     * @param fx     the x component of the force to apply [N]
     * @param fy     the y component of the force to apply [N]
     * @param fz     the z component of the force to apply [N]
     * @param tx     the x component of the torque to apply [Nm]
     * @param ty     the y component of the torque to apply [Nm]
     * @param tz     the z component of the torque to apply [Nm]
     */
    @ApiStatus.Internal
    static native void applyForceAndTorque(final long sceneHandle, final int bodyID, final double fx, final double fy, final double fz, final double tx, final double ty, final double tz, final boolean wakeUp);

    /**
     * Gets the linear velocity of a given body
     *
     * @param bodyID the ID of an already created rigid-body
     * @param store  The array to store the linear velocity of the body in the format [x, y, z]
     */
    @ApiStatus.Internal
    static native void getLinearVelocity(final long sceneHandle, final int bodyID, final double[] store);

    /**
     * Gets the angular velocity of a given body
     *
     * @param bodyID the ID of an already created rigid-body
     * @param store  The array to store the angular velocity of the body in the format [x, y, z]
     */
    @ApiStatus.Internal
    static native void getAngularVelocity(final long sceneHandle, final int bodyID, final double[] store);

    /**
     * Creates a kinematic sub-level within a scene.
     *
     * @param mountId the mount rigid body ID (or -1 for ground)
     * @param id      the kinematic sub-level ID
     * @param pose    a 7-long double array, formatted [x, y, z, qx, qy, qz, qw] for position and quaternion
     */
    @ApiStatus.Internal
    static native void createKinematicContraption(final long sceneHandle, int mountId, int id, double[] pose);

    /**
     * Removes a kinematic sub-level from a scene.
     *
     * @param id      the kinematic sub-level ID to remove
     */
    @ApiStatus.Internal
    static native void removeKinematicContraption(final long sceneHandle, int id);

    /**
     * Sets the transform (position/quaternion) of a kinematic sub-level's center of mass relative to its parent.
     *
     * @param id      the kinematic sub-level ID
     * @param pose    a 7-long double array, formatted [x, y, z, qx, qy, qz, qw] for position and quaternion
     */
    @ApiStatus.Internal
    static native void setKinematicContraptionTransform(final long sceneHandle, int id, double[] centerOfMass, double[] pose, double[] velocities);

    /**
     * Adds a chunk to a kinematic sub-level (4096 blocks, each as packed int).
     *
     * @param id      the kinematic sub-level ID
     * @param x       the chunk x coordinate
     * @param y       the chunk y coordinate
     * @param z       the chunk z coordinate
     * @param data    a 4096-long int array containing packed block data (block_collider_id << 16 | voxel_state_id)
     */
    @ApiStatus.Internal
    static native void addKinematicContraptionChunkSection(final long sceneHandle, int id, int x, int y, int z, int[] data);

    /**
     * Creates a rope
     *
     * @return a rope id
     */
    @ApiStatus.Internal
    public static native long createRope(final long sceneHandle, final double pointRadius, final double firstJointLength, final double[] points, final int pointCount);

    /**
     * Removes a rope
     *
     * @param ropeId a rope id
     */
    @ApiStatus.Internal
    public static native long removeRope(final long sceneHandle, final long ropeId);

    @ApiStatus.Internal
    public static native void setRopeAttachment(final long sceneHandle, final long ropeId, final int subLevelId, final double x, final double y, final double z, final boolean end);

    @ApiStatus.Internal
    public static native void addRopePointAtStart(final long sceneHandle, final long ropeId, final double x, final double y, final double z);

    @ApiStatus.Internal
    public static native void removeRopePointAtStart(final long sceneHandle, final long ropeId);

    @ApiStatus.Internal
    public static native void wakeUpRope(final long sceneHandle, final long ropeId);

    @ApiStatus.Internal
    public static native void setRopeFirstSegmentLength(final long sceneHandle, final long ropeId, final double firstSegmentLength);

    /**
     * Queries a rope
     *
     * @param ropeId a rope id
     */
    @ApiStatus.Internal
    public static native double[] queryRope(final long sceneHandle, final long ropeId);

    @ApiStatus.Internal
    static native void configFrequencyAndDamping(
            double contactNaturalFrequency,
            double contactDampingRatio);

    @ApiStatus.Internal
    static native void configSolverIterations(int solverIterations, int pgsIterations, int stabilizationIterations);

    @ApiStatus.Internal
    static native void configMinIslandSize(int islandSize);

    @ApiStatus.Internal
    static native void dispose(long sceneHandle);

    @ApiStatus.Internal
    static void setMassPropertiesFrom(final long sceneHandle, final int id, final MassData massTracker) {
        final Matrix3dc inertiaTensor = massTracker.getInertiaTensor();
        final Vector3dc centerOfMass = massTracker.getCenterOfMass();
        final double mass = massTracker.getMass();

        // This is only called in one location and the center of mass can't be null
        //noinspection DataFlowIssue
        final double[] centerOfMassArray = new double[]{centerOfMass.x(), centerOfMass.y(), centerOfMass.z()};
        final double[] inertiaTensorArray = new double[]{
                inertiaTensor.m00(), inertiaTensor.m01(), inertiaTensor.m02(),
                inertiaTensor.m10(), inertiaTensor.m11(), inertiaTensor.m12(),
                inertiaTensor.m20(), inertiaTensor.m21(), inertiaTensor.m22()
        };

        Rapier3D.setMassProperties(sceneHandle, id, mass, centerOfMassArray, inertiaTensorArray);
    }
}
