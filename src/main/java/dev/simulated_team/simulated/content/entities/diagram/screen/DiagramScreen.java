package dev.simulated_team.simulated.content.entities.diagram.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.simibubi.create.foundation.gui.RemovedGuiUtils;
import dev.ryanhcode.sable.MobilePlatform;
import dev.ryanhcode.sable.SableClientConfig;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.entities.diagram.DiagramConfig;
import dev.simulated_team.simulated.content.entities.diagram.DiagramEntity;
import dev.simulated_team.simulated.data.SimLang;
import dev.simulated_team.simulated.index.SimGUITextures;
import dev.simulated_team.simulated.index.SimResourceManagers;
import dev.simulated_team.simulated.network.packets.contraption_diagram.DiagramDataPacket;
import dev.simulated_team.simulated.network.packets.contraption_diagram.DiagramSaveConfigPacket;
import dev.simulated_team.simulated.network.packets.contraption_diagram.RequestDiagramDataPacket;
import dev.simulated_team.simulated.util.SimpleSubLevelGroupRenderer;
import foundry.veil.api.client.render.VeilLevelPerspectiveRenderer;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.post.PostProcessingManager;
import foundry.veil.api.network.VeilPacketManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.lang.LangBuilder;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Tuple;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.*;

import java.lang.Math;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import org.joml.Quaternionf;
import dev.simulated_team.simulated.network.SimPacketManager;

public class DiagramScreen extends AbstractSimiScreen {
    public static int UPDATE_REQUEST_INTERVAL = 10;

    public static final Color TEXT_COLOR = new Color(79, 82, 87);
    public static final Color BUTTON_COLOR = new Color(109, 113, 119);
    public static final Color DULL_BUTTON_COLOR = new Color(181, 177, 168);
    public static final Color BG_COLOR = new Color(247, 240, 221);
    private static final int TOOLTIP_LABEL_COLOR = 0xffc2937d;

    private static final int MIN_ARROW_SIZE_PX = 6;

    public static final float PAPER_SLIDE_SPEED = 0.4f;
    public static final float TAB_SLIDE_SPEED = 0.1f;
    public static final int MAX_PAPER_OFFSET = SimGUITextures.DIAGRAM_PAPER.width + 3;
    public static final int MIN_PAPER_OFFSET = 10;

    private static final Vector3d LOCAL_CAMERA_POSITION = new Vector3d();
    private static final Vector3d CAMERA_POSITION = new Vector3d();
    private static final Matrix4f PROJECTION_MAT = new Matrix4f();
    public static final Quaternionf LOCAL_ORIENTATION = new Quaternionf();

    private static final Vector2d MAGNIFYING_CENTER = new Vector2d();
    private static final Vector2d MAGNIFYING_MAX = new Vector2d();
    private static final Vector2d MAGNIFYING_MIN = new Vector2d();
    private static final int MIN_MAGNIFICATION_PIXELS = 3;

    public static final SimGUITextures DIAGRAM_TEXTURE = SimGUITextures.DIAGRAM;
    public static final float FPS = 12.0f;

    private final DiagramEntity diagram;
    public final ClientSubLevel subLevel;

    protected DiagramConfig config;
    private boolean configDirty = false;

    private final List<DiagramForceGroupToggle> forceToggleWidgets = new ObjectArrayList<>();

    private AdvancedFbo fbo;
    private AdvancedFbo outlineFbo;
    private AdvancedFbo finalFbo;

    // [手机端优化·B1] 主图解离屏 FBO 的**实际像素尺寸**。
    // GUI 显示尺寸恒为 DIAGRAM_TEXTURE.width/height，renderFBO 以 UV 0~1 全图铺满，
    // 因此降像素只影响图样清晰度，不改变界面布局与鼠标命中区。
    private int fboWidth = DIAGRAM_TEXTURE.width;
    private int fboHeight = DIAGRAM_TEXTURE.height;

    private float renderTime = FPS;

    private boolean paperVisible = false;
    private float lastPaperOffset = MIN_PAPER_OFFSET;
    private float paperOffset = MIN_PAPER_OFFSET;
    private float lastTabOffset = 0;
    private float tabOffset = 0;

    public final List<FormattedText> tooltipList = new ArrayList<>();

    
    private DiagramDataPacket serverData = null;
    private float viewportRadius;

    private int ticksWithoutUpdate = 0;

    private DiagramButton turnUpButton;
    private DiagramButton turnDownButton;

    private DiagramButton mergeButton;

    private boolean magnifying = false;

    private DiagramStickyNote note;

    // ================= [1.20.1 移植·图纸自由摆放] =================
    // 需求：主图解板（连同左侧配置纸、板上所有按钮）与便签，两者都能右键拖拽；
    //       拖谁谁显示在最上层；便签在未被拖到最上层时默认压在最底层。
    // 主图解板整体拖拽偏移（相对屏幕基准位置），允许拖出屏幕外，与便签行为一致。
    private float diagramDragOffsetX = 0.0f;
    private float diagramDragOffsetY = 0.0f;
    private float diagramDragTargetX = 0.0f;
    private float diagramDragTargetY = 0.0f;
    private boolean diagramDragging = false;
    private int diagramGrabX = 0;
    private int diagramGrabY = 0;

    // 层级标记：true = 便签在最上层；false（默认）= 主图解板在上、便签压最底层。
    private boolean noteOnTop = false;

    // 随主图解板一起平移的控件及其基准坐标（基准 = 不含拖拽偏移的原始摆放位置）。
    private final List<AbstractWidget> followWidgets = new ObjectArrayList<>();
    private final List<int[]> followWidgetBases = new ObjectArrayList<>();

    public DiagramScreen(final DiagramEntity diagramEntity, final ClientSubLevel subLevel) {
        this.diagram = diagramEntity;
        this.subLevel = subLevel;
    }

    public static void open(final DiagramEntity diagramEntity, final DiagramConfig config, final SubLevel subLevel) {
        final Minecraft minecraft = Minecraft.getInstance();
        final DiagramScreen screen = new DiagramScreen(diagramEntity, (ClientSubLevel) subLevel);

        screen.config = config;
        screen.updateViewportOrientation();

        minecraft.setScreen(screen);
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 1.0f));
    }

    private void updateViewportOrientation() {
        this.renderTime = Float.MAX_VALUE;
        LOCAL_ORIENTATION.identity().rotateY((float) Math.toRadians(this.config.yaw())).rotateX((float) Math.toRadians(this.config.pitch()));
    }

    private void freeFramebuffers() {
        if (this.note != null) {
            this.note.free();
        }

        if (this.fbo != null) {
            this.fbo.free();
            this.fbo = null;

            this.outlineFbo.free();
            this.outlineFbo = null;

            this.finalFbo.free();
            this.finalFbo = null;
        }
    }

    @Override
    public void onClose() {
        super.onClose();
        this.freeFramebuffers();
    }

    @Override
    protected void init() {
        super.init();
        this.freeFramebuffers();
        // [手机端优化·B1] 主图解是玩家的主视图，降分辨率比便签更保守：最多只降到一半。
        this.fboWidth = mobileFboSize(DIAGRAM_TEXTURE.width);
        this.fboHeight = mobileFboSize(DIAGRAM_TEXTURE.height);

        this.fbo = AdvancedFbo.withSize(this.fboWidth, this.fboHeight).addColorTextureBuffer().setDepthTextureBuffer().build(true);
        this.outlineFbo = AdvancedFbo.withSize(this.fboWidth, this.fboHeight).addColorTextureBuffer().build(true);
        this.finalFbo = AdvancedFbo.withSize(this.fboWidth, this.fboHeight).addColorTextureBuffer().build(true);

        // [1.20.1 移植·图纸自由摆放] 控件一律按「基准坐标」摆放，拖拽偏移在 tick 阶段统一叠加，
        // 这样重复调用 init（如窗口缩放）不会把偏移量重复累加进控件坐标。
        final int diagramX = this.getDiagramBaseX();
        final int diagramY = this.getDiagramBaseY();

        this.note = new DiagramStickyNote(this, diagramX, diagramY, Component.empty(), () -> {
        });
        this.note.create(this.config.getNoteConfigs());

        if (this.subLevel.isRemoved()) {
            this.onClose();
            return;
        }

        this.renderContents(this.subLevel, 0);

        for (int i = 0; i < 1; i++) {
            this.addGreebles(diagramX, diagramY);
        }

//        final DiagramButton glass = new DiagramButton(SimGUITextures.DIAGRAM_ICON_MAGNIFYING_GLASS, diagramX + 18 + 11, diagramY + 9, Component.empty(), () -> {
//            this.magnifying = !this.magnifying;
//        }).setDiagramTooltip(() -> SimLang.text("Magnify Selection").component()).setIconSwitch(this::isMagnifying);

        final DiagramButton forceButton = new DiagramButton(SimGUITextures.DIAGRAM_ICON_FORCES, diagramX + 9, diagramY + 9, Component.empty(), () -> {
            this.paperVisible = !this.paperVisible;
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        }).setDiagramTooltip(() -> SimLang.translate("contraption_diagram.toggle_paper").component());

        this.mergeButton = new DiagramButton(this.getMergeIcon(), diagramX + 9, diagramY + 9 + 20, Component.empty(), () -> {
            this.config.setMergeForces(!this.config.mergeForces());
            this.mergeButton.setTexture(this.getMergeIcon());
            this.setConfigDirty();
        }).setDiagramTooltip(() -> {
            return SimLang.translate("contraption_diagram.merge_forces").color(TOOLTIP_LABEL_COLOR).add(SimLang.translate(this.config.mergeForces() ? "contraption_diagram.merged" : "contraption_diagram.unmerged").color(0xffffffff)).component();
        });

        final DiagramButton centerOfMassButton = new DiagramButton(SimGUITextures.DIAGRAM_ICON_COM_TOGGLE, diagramX + 9, diagramY + 9 + 20 * 2, Component.empty(), () -> {
            this.config.setDisplayCenterOfMass(!this.config.displayCenterOfMass());
            this.setConfigDirty();
        }).setDiagramTooltip(() -> {
            return SimLang.translate("contraption_diagram.center_of_mass").color(TOOLTIP_LABEL_COLOR).add(SimLang.translate(this.config.displayCenterOfMass() ? "contraption_diagram.shown" : "contraption_diagram.hidden").color(0xffffffff)).component();
        });

        final DiagramButton massButton = new DiagramButton(SimGUITextures.DIAGRAM_ICON_MASS, diagramX + 9, diagramY + 9 + 20 * 3, Component.empty(), () -> {

        }).setDiagramTooltip(() -> {
            final String massString = this.serverData != null ? String.format("%,.2f", this.serverData.mass()) : "---";
            return SimLang.translate("contraption_diagram.total_mass").color(TOOLTIP_LABEL_COLOR).add(SimLang.translate("contraption_diagram.mass", massString).color(0xffffffff)).component();
        });

        massButton.active = false;

        this.addRenderableWidget(forceButton);
        this.addRenderableWidget(centerOfMassButton);
        this.addRenderableWidget(massButton);
        this.addRenderableWidget(this.mergeButton);

        this.addRotationGizmo(diagramX, diagramY);
        this.addForceToggleWidgets(diagramX, diagramY);

        this.addWidget(this.note);

        this.collectFollowWidgets();
        this.applyDiagramDragToWidgets();
    }

    /**
     * [1.20.1 移植·图纸自由摆放]
     * 收集所有需要随主图解板一起平移的控件，并记录它们的基准坐标。
     * 便签是独立可拖拽的另一张纸，故排除在外。
     */
    private void collectFollowWidgets() {
        this.followWidgets.clear();
        this.followWidgetBases.clear();

        for (final GuiEventListener child : this.children()) {
            if (child instanceof AbstractWidget widget && widget != this.note) {
                this.followWidgets.add(widget);
                this.followWidgetBases.add(new int[]{widget.getX(), widget.getY()});
            }
        }
    }

    /** 把主图解板当前的拖拽偏移应用到所有跟随控件上。 */
    private void applyDiagramDragToWidgets() {
        final int offsetX = (int) this.diagramDragOffsetX;
        final int offsetY = (int) this.diagramDragOffsetY;

        for (int i = 0; i < this.followWidgets.size(); i++) {
            final int[] base = this.followWidgetBases.get(i);
            final AbstractWidget widget = this.followWidgets.get(i);
            widget.setX(base[0] + offsetX);
            widget.setY(base[1] + offsetY);
        }
    }

    // [1.20.1 移植] 主图解位于正中心偏右：在屏幕水平居中基础上右移固定偏置（DIAGRAM_X_RIGHT_BIAS）。
    // 便签现在可无限制拖拽（可拖出屏幕），不再需要为主图解预留便签滑出空间，故去掉原左移 hack。
    private static final int DIAGRAM_X_RIGHT_BIAS = 16;

    /** 主图解板的屏幕基准横坐标（不含拖拽偏移）。控件初始摆放以此为准。 */
    private int getDiagramBaseX() {
        final int x = this.width / 2 - DIAGRAM_TEXTURE.width / 2 + DIAGRAM_X_RIGHT_BIAS;
        return Math.max(0, x);
    }

    /** 主图解板的屏幕基准纵坐标（不含拖拽偏移）。 */
    private int getDiagramBaseY() {
        return this.height / 2 - DIAGRAM_TEXTURE.height / 2;
    }

    /** 主图解板当前横坐标（含右键拖拽偏移）。渲染与命中判定一律使用本方法。 */
    public int getDiagramX() {
        return this.getDiagramBaseX() + (int) this.diagramDragOffsetX;
    }

    /** 主图解板当前纵坐标（含右键拖拽偏移）。 */
    public int getDiagramY() {
        return this.getDiagramBaseY() + (int) this.diagramDragOffsetY;
    }

    private void addRotationGizmo(final int diagramX, final int diagramY) {
        this.turnUpButton = new DiagramButton(SimGUITextures.DIAGRAM_ICON_TURN_UP, diagramX + 236, diagramY + 8, Component.empty(), () -> {
            this.rotateDiagram(0, -1);
        });
        this.turnDownButton = new DiagramButton(SimGUITextures.DIAGRAM_ICON_TURN_DOWN, diagramX + 236, diagramY + 8 + 14, Component.empty(), () -> {
            this.rotateDiagram(0, 1);
        });
        final DiagramButton turnLeftButton = new DiagramButton(SimGUITextures.DIAGRAM_ICON_TURN_LEFT, diagramX + 228, diagramY + 12, Component.empty(), () -> {
            this.rotateDiagram(1, 0);
        });
        final DiagramButton turnRightButton = new DiagramButton(SimGUITextures.DIAGRAM_ICON_TURN_RIGHT, diagramX + 243, diagramY + 12, Component.empty(), () -> {
            this.rotateDiagram(-1, 0);
        });

        this.addRenderableWidget(this.turnUpButton);
        this.addRenderableWidget(this.turnDownButton);
        this.addRenderableWidget(turnLeftButton);
        this.addRenderableWidget(turnRightButton);
    }

    private void rotateDiagram(int yawSteps, final int pitchSteps) {
        if (this.config.pitch() > 45.0) {
            yawSteps = -yawSteps;
        }

        this.config.setYaw(this.config.yaw() + yawSteps * 90.0f);
        this.config.setPitch(Mth.clamp(this.config.pitch() + pitchSteps * 90.0f, -90.0f, 90.0f));
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 1.0f));

        this.updateViewportOrientation();
        this.setConfigDirty();
    }

    private void addForceToggleWidgets(final int diagramX, final int diagramY) {
        final Iterable<ForceGroup> forceGroups = ForceGroups.REGISTRY.values();

        this.forceToggleWidgets.clear();

        int i = 0;
        for (final ForceGroup forceGroup : forceGroups) {
            final int yOffset = 11 * (i + 1) - 1;
            final int xOffset = -MAX_PAPER_OFFSET - 6;
            final DiagramForceGroupToggle widget = new DiagramForceGroupToggle(this, forceGroup, diagramX + xOffset, diagramY + yOffset);
            this.addWidget(widget);

            this.forceToggleWidgets.add(widget);
            i++;
        }
    }

    // this is horrid :(
    private HashMap<ResourceLocation, Tuple<Greeble, ArrayList<Greeble.TextureSlice>>> genGreebleSet(final RandomSource random) {
        final HashMap<ResourceLocation, Tuple<Greeble, ArrayList<Greeble.TextureSlice>>> greebleSet = new HashMap<>();

        for (final Map.Entry<ResourceLocation, Greeble> entry : SimResourceManagers.GREEBLE.entrySet()) {
            greebleSet.put(entry.getKey(), new Tuple<>(entry.getValue(), entry.getValue().shuffled()));
        }

        return greebleSet;
    }

    private ResourceLocation randomGreeble(final RandomSource random) {
        float weightSum = 0;

        for (final Greeble greeble : SimResourceManagers.GREEBLE.entries()) {
            weightSum += greeble.weight();
        }

        float weight = random.nextFloat() * weightSum;

        for (final Map.Entry<ResourceLocation, Greeble> greeble : SimResourceManagers.GREEBLE.entrySet()) {
            weight -= greeble.getValue().weight();

            if (weight <= 0) {
                return greeble.getKey();
            }
        }
        throw new RuntimeException();
    }

    private void addGreebles(final int diagramX, final int diagramY) {
        final RandomSource random = this.subLevel.getLevel().getRandom();

        final HashMap<ResourceLocation, Tuple<Greeble, ArrayList<Greeble.TextureSlice>>> greebleSet = this.genGreebleSet(random);
        final List<AABB> placed = new ObjectArrayList<>();

        // Avoid top-left region (diagram buttons are placed there)
        placed.add(new AABB(0, 0, 0, 26, 66, 1));

        // Avoid rotation gizmo
        placed.add(new AABB(227, 8, 0, 250, 28, 1));

        final int padding = 10;
        final int greebles = 8;

        this.finalFbo.bindRead();

        for (int i = 0; i < greebles; i++) {
            final ResourceLocation greebleID = this.randomGreeble(random);
            final Greeble greeble = SimResourceManagers.GREEBLE.get(greebleID);
            final ArrayList<Greeble.TextureSlice> slices = greebleSet.get(greebleID).getB();
            if (slices.isEmpty()) {
                continue;
            }
            final Greeble.TextureSlice slice = slices.remove(0);

            final int x = random.nextInt(padding, DIAGRAM_TEXTURE.width - slice.width() - padding);
            final int y = random.nextInt(padding, DIAGRAM_TEXTURE.height - slice.height() - padding);

            final AABB box = new AABB(x, y, 0, x + slice.width(), y + slice.height(), 1);
            boolean intersects = false;
            for (final AABB aabb : placed) {
                if (box.intersects(aabb)) {
                    intersects = true;
                    break;
                }
            }

            if (intersects || this.aabbInFramebuffer(box)) {
                continue;
            }

            placed.add(box);
            // [1.20.1 移植·图纸自由摆放] 存相对坐标，绘制时再叠加主图解板当前位置，随板一起移动。
            this.addRenderableOnly(new GreebleRenderable(this, x, y, greeble.width(), greeble.height(), greeble.texture(), slice));
        }

        AdvancedFbo.unbind();
    }

    private boolean aabbInFramebuffer(final AABB aabb) {
        final int minX = (int) aabb.minX;
        final int minY = (int) (DIAGRAM_TEXTURE.height - aabb.minY);
        final int maxX = (int) aabb.maxX;
        final int maxY = (int) (DIAGRAM_TEXTURE.height - aabb.maxY);

        final int width = Math.abs(maxX - minX);
        final int height = Math.abs(maxY - minY);

        final int length = width * height;
        final int[] buffer = new int[length];
        glReadPixels(minX, minY - height, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

        for (int i = 0; i < length; i++) {
            final int color = buffer[i] >> 24;
            if (color != 0) return true;
        }
        return false;
    }

    /**
     * [手机端优化·B1 / F1] 把主图解的离屏分辨率按手机端配置缩放。
     *
     * <p>非手机端原样返回（PC 行为零变化）。手机端取 {@code MOBILE_FBO_SCALE}，
     * 但主图解是玩家实际盯着看的主视图，这里下限夹到 0.5，最多只降一半，避免糊得看不清；
     * 只有翻译层 / 安全帧缓冲模式才允许再往下压（同样不低于 0.5 的一半，即 0.25）。
     */
    private static int mobileFboSize(final int original) {
        if (!MobilePlatform.isMobile()) {
            return original;
        }
        double scale;
        try {
            scale = SableClientConfig.MOBILE_FBO_SCALE.get();
        } catch (final Throwable t) {
            scale = 1.0;
        }
        scale = Math.max(0.5, Math.min(1.0, scale));
        if (MobilePlatform.isSafeFramebufferMode()) {
            scale *= 0.5;
        }
        return Math.max(32, (int) java.lang.Math.round(original * scale));
    }

    /**
     * [手机端优化·B3] 主图解离屏重绘的间隔倍数。
     *
     * <p>每次重绘都要把整条子关卡链（地形层 + 方块实体 + 实体）完整画一遍，
     * 是打开图解界面时最重的开销。手机端拉长间隔：原生 GPU 档 ×1.5，翻译层档 ×2.5；
     * 非手机端为 ×1，判据与源版逐位一致。
     */
    private static float mobileRenderIntervalScale() {
        if (!MobilePlatform.isMobile()) {
            return 1.0f;
        }
        return MobilePlatform.isTranslationLayer() ? 2.5f : 1.5f;
    }

    /**
     * [手机端优化·B2 / F1] 是否跳过描边后处理，改用廉价直拷贝。
     *
     * <p>安全帧缓冲模式（翻译层缺关键 GL 特性）下强制跳过，避免黑屏或崩溃；
     * 否则听从客户端配置 {@code MOBILE_DISABLE_OUTLINE_POST}。非手机端恒为 false。
     */
    private static boolean skipOutlinePostProcess() {
        if (!MobilePlatform.isMobile()) {
            return false;
        }
        if (MobilePlatform.isSafeFramebufferMode()) {
            return true;
        }
        try {
            return SableClientConfig.MOBILE_DISABLE_OUTLINE_POST.get();
        } catch (final Throwable t) {
            return false;
        }
    }

    private void renderContents(final SubLevel subLevel, final float partialTicks) {
        final Minecraft minecraft = Minecraft.getInstance();

        // Only render at framerate
        // [手机端优化·B3] 手机端拉长重绘间隔（非手机端倍数为 1，与源版完全一致）。
        if (this.renderTime >= (20.0f / FPS) * mobileRenderIntervalScale()) {
            this.renderTime = 0.0f;
        } else {
            this.renderTime += minecraft.getFrameTime();
            return;
        }

        if (this.fbo == null) {
            return;
        }

        final float zNear = 0.1f;
        final LevelPlot plot = subLevel.getPlot();

        // [BUG36·修复] subBounds = subLevel.boundingBox() 即几何真实占据空间（渲染坐标系），
        // 优先用它算缩放半径；物理化气球的 plot 包围盒是 plot 空间尺寸、与几何渲染尺寸不同。
        final BoundingBox3dc subBounds = subLevel.boundingBox();
        final BoundingBox3ic plotBounds = plot.getBoundingBox();
        float radius;
        if (subBounds != null && subBounds.volume() > 0) {
            radius = (float)(Math.max(Math.max(subBounds.maxX() - subBounds.minX(), subBounds.maxY() - subBounds.minY()), subBounds.maxZ() - subBounds.minZ()) + 1);
        } else {
            radius = Math.max(Math.max(plotBounds.maxX() - plotBounds.minX(), plotBounds.maxY() - plotBounds.minY()), plotBounds.maxZ() - plotBounds.minZ()) + 1;
        }
        radius *= 0.7F;

        radius = Math.max(radius, 2.0f);

        this.viewportRadius = radius;

        // [BUG36·修复] 相机中心用渲染坐标系下的包围盒中心（subBounds 中心），
        // 即几何实际占据空间的中心；不再用 plot.getBoundingBox() 的 plot 空间坐标（物理化后约 2000 万）。
        final Vector3d renderCenter;
        if (subBounds != null && subBounds.volume() > 0) {
            renderCenter = subBounds.center(new Vector3d());
        } else {
            // 兜底：极少见的空包围盒，退回旧行为（plot 中心）
            renderCenter = new Vector3d((plotBounds.minX() + plotBounds.maxX() + 1) / 2.0, (plotBounds.minY() + plotBounds.maxY() + 1) / 2.0, (plotBounds.minZ() + plotBounds.maxZ() + 1) / 2.0);
        }

        // [手机端优化·B1] 宽高比按 FBO 实际像素尺寸算（等比缩放时数值不变，构图完全一致）。
        final float aspect = (float) this.fboWidth / this.fboHeight;
        PROJECTION_MAT.identity().ortho(-radius * aspect, radius * aspect, -radius, radius, zNear, radius * 2.0f);

        // account for the smaller screen size
        LOCAL_CAMERA_POSITION.set(renderCenter.add(LOCAL_ORIENTATION.transform(new Vector3d(0, 0, radius))));

        // 相机位置已是渲染坐标系（与几何 renderPos 一致），不再经 renderPose 二次变换
        CAMERA_POSITION.set(LOCAL_CAMERA_POSITION);

        // [手机端优化·B1] 描边着色器的 InSize 必须是 FBO 的真实像素尺寸（用于逐 texel 求边缘）。
        draw(subLevel, partialTicks, LOCAL_ORIENTATION, PROJECTION_MAT, CAMERA_POSITION, this.fboWidth, this.fboHeight, this.fbo, this.outlineFbo, this.finalFbo, 0.25f, 1.0f, 0x2E3032, 0x696965);
    }

    public static void draw(final SubLevel subLevel, final float partialTicks, final Quaternionf localOrientation, final Matrix4f projMatrix, final Vector3d cameraPos, final float inWidth, final float inHeight, final AdvancedFbo fbo, final AdvancedFbo outlineFbo, final AdvancedFbo finalFbo, final float paletteOffset, final float fadeScale, final int lineColor, final int lineShadowColor) {
        fbo.bind(true);
        // [BUG40·修复] 清屏同样受颜色写掩码约束：掩码若被上游关掉，clear() 根本清不掉颜色，
        // 上一帧的残留会一直留在缓冲里。这里在清屏前先把颜色/深度写掩码强制打开。
        com.mojang.blaze3d.systems.RenderSystem.colorMask(true, true, true, true);
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        fbo.clear();

        // [BUG36·修复] 不再用 renderPose.orientation() 的共轭：物理化气球的 renderPose 含旋转，
        // 原写法会让离屏相机朝向被物理旋转带偏，而几何已在 renderChunkedSubLevel 内部按
        // renderRot 旋转到世界坐标，于是被转到视野外 → 预览空白（cutout 层仍「提交绘制 2 次」，
        // 只是整块被裁出视锥）。几何已是世界坐标，相机只用用户预览朝向 localOrientation 即可正对；
        // 非物理化时 renderRot=单位，orientation 本就等于 localOrientation.conjugate，行为不变。
        final Quaternionf orientation = new Quaternionf(localOrientation.conjugate(new Quaternionf()));

        SimpleSubLevelGroupRenderer.renderChain(subLevel, fbo, new Matrix4f(), projMatrix, cameraPos, orientation, partialTicks);

        // [1.20.1 移植] 源版此处走 Veil 后处理管线 simulated:diagram：
        // 以 fbo 的颜色+深度为输入，outline_diagram 着色器做深度描边+调色板抖动，
        // 输出"蓝图线稿"风格图样到 finalFbo（GUI 显示的是 finalFbo）。
        // 本工程 Veil 管线为空壳，改用自建 GL 后处理等价复刻同一步。
        // [手机端优化·B2 / F1] 手机端可关闭描边后处理：全屏着色器 + 4 张纹理逐像素采样，
        // 在翻译层（gl4es/VirGL）上是图解界面最贵的固定开销。关闭后退化为直接拷贝颜色缓冲。
        if (skipOutlinePostProcess()) {
            DiagramOutlinePostProcess.runCheapCopy(fbo, finalFbo);
        } else {
            DiagramOutlinePostProcess.run(fbo, finalFbo, inWidth, inHeight, paletteOffset, fadeScale, lineColor, lineShadowColor);
        }
    }

    @Override
    protected void renderWindowBackground(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTicks) {
        graphics.fill(0, 0, this.width, this.height, -10, 0x4fffffff);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.subLevel.isRemoved() || this.diagram.isRemoved()) {
            this.onClose();
            return;
        }

        if (this.configDirty) {
            dev.simulated_team.simulated.network.SimPacketManager.INSTANCE.server().sendPacket(new DiagramSaveConfigPacket(this.diagram.getId(), this.config));
            this.configDirty = false;
        }

        if (this.ticksWithoutUpdate++ > UPDATE_REQUEST_INTERVAL) {
            this.ticksWithoutUpdate = 0;
            dev.simulated_team.simulated.network.SimPacketManager.INSTANCE.server().sendPacket(new RequestDiagramDataPacket(this.subLevel.getUniqueId()));
        }

        this.lastPaperOffset = this.paperOffset;
        this.paperOffset = Mth.lerp(PAPER_SLIDE_SPEED, this.paperOffset, this.paperVisible ? MAX_PAPER_OFFSET : MIN_PAPER_OFFSET);
        this.lastTabOffset = this.tabOffset;
        //this.tabOffset = Mth.lerp(TAB_SLIDE_SPEED, this.tabOffset, (this.paperOffset-MIN_PAPER_OFFSET)/(MAX_PAPER_OFFSET-MIN_PAPER_OFFSET));
        this.tabOffset = Mth.lerp(this.paperVisible ? PAPER_SLIDE_SPEED : TAB_SLIDE_SPEED, this.tabOffset, this.paperVisible ? 1 : 0);
        //this.tabOffset = Math.max(this.tabOffset-0.1f,nextTabOffset);

        // [1.20.1 移植·图纸自由摆放] 主图解板拖拽同样走平滑插值，手感与便签一致。
        if (this.diagramDragging) {
            this.diagramDragOffsetX = Mth.lerp(PAPER_SLIDE_SPEED, this.diagramDragOffsetX, this.diagramDragTargetX);
            this.diagramDragOffsetY = Mth.lerp(PAPER_SLIDE_SPEED, this.diagramDragOffsetY, this.diagramDragTargetY);
        }
        this.applyDiagramDragToWidgets();

        this.note.tick();
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        // [1.20.1 移植] 右键在便签上且滑入动画已结束 → 直接启动拖拽。
        // 必须在此亲自接管，不能依赖 ContainerEventHandler 的拖拽转发：
        // 其 mouseDragged 仅在 isDragging()==true 时才转发，而 isDragging 只有左键
        // mouseClicked 成功消费后才会被置 true，右键点击不会触发，导致拖拽失效。
        if (button == 1) {
            // [1.20.1 移植·图纸自由摆放] 主图解板与便签均可右键拖拽。
            // 判定顺序按当前层级从上到下：位于上层的那张纸优先响应，符合视觉直觉。
            if (this.noteOnTop) {
                if (this.tryBeginNoteDrag(mouseX, mouseY) || this.tryBeginDiagramDrag(mouseX, mouseY)) {
                    return true;
                }
            } else {
                if (this.tryBeginDiagramDrag(mouseX, mouseY) || this.tryBeginNoteDrag(mouseX, mouseY)) {
                    return true;
                }
            }
        }

        final boolean widgetPress = super.mouseClicked(mouseX, mouseY, button);

        // [1.20.1 移植] 仅左键参与框选/激活手势；右键留给便签拖拽，不记录放大中心。
        if (button == 0) {
            final boolean withinNote = this.note.contains(mouseX, mouseY);
            if (withinNote || (!widgetPress && this.contains(mouseX, mouseY) /*&& this.isMagnifying()*/)) {
//                if (!withinNote) {
//                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SimSoundEvents.DIAGRAM_TAP.event(), 1.0f));
//                }

                MAGNIFYING_CENTER.set(mouseX, mouseY);
            }
        }

        return widgetPress;
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dx, final double dy) {
        if (this.note.isDragging()) {
            this.note.dragTo(mouseX, mouseY);
            return true;
        }

        if (this.diagramDragging) {
            this.diagramDragTargetX = (float) (mouseX - this.diagramGrabX - this.getDiagramBaseX());
            this.diagramDragTargetY = (float) (mouseY - this.diagramGrabY - this.getDiagramBaseY());
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button) {
        // [1.20.1 移植] 便签拖拽进行中 → 结束拖拽并跳过框选逻辑（避免误激活）。
        if (this.note.isDragging()) {
            this.note.endDrag();
            MAGNIFYING_CENTER.set(0, 0);
            MAGNIFYING_MAX.set(0, 0);
            return true;
        }

        // [1.20.1 移植·图纸自由摆放] 主图解板拖拽收尾：直接吸附到目标偏移，避免松手后继续缓动。
        if (this.diagramDragging) {
            this.diagramDragging = false;
            this.diagramDragOffsetX = this.diagramDragTargetX;
            this.diagramDragOffsetY = this.diagramDragTargetY;
            this.applyDiagramDragToWidgets();
            MAGNIFYING_CENTER.set(0, 0);
            MAGNIFYING_MAX.set(0, 0);
            return true;
        }

        final boolean parent = super.mouseReleased(mouseX, mouseY, button);

        // [1.20.1 移植] 仅左键触发框选/激活逻辑。
        if (button == 0) {
            this.updateNote(mouseX, mouseY, parent);
        }

        // no matter what clear start and end positions
        MAGNIFYING_CENTER.set(0, 0);
        MAGNIFYING_MAX.set(0, 0);
        return parent;
    }

    /**
     * [1.20.1 移植·图纸自由摆放]
     * 尝试对便签起手右键拖拽。成功则把便签提到最上层。
     */
    private boolean tryBeginNoteDrag(final double mouseX, final double mouseY) {
        if (!this.note.isMouseOver(mouseX, mouseY) || !this.note.isSlideDone()) {
            return false;
        }

        this.note.beginDrag(mouseX, mouseY);
        this.noteOnTop = true;
        return true;
    }

    /**
     * [1.20.1 移植·图纸自由摆放]
     * 尝试对主图解板起手右键拖拽。成功则把主图解板提到最上层（便签退回最底层）。
     */
    private boolean tryBeginDiagramDrag(final double mouseX, final double mouseY) {
        if (!this.overDiagramBoard(mouseX, mouseY)) {
            return false;
        }

        this.diagramDragging = true;
        this.diagramGrabX = (int) (mouseX - this.getDiagramX());
        this.diagramGrabY = (int) (mouseY - this.getDiagramY());
        this.diagramDragTargetX = this.diagramDragOffsetX;
        this.diagramDragTargetY = this.diagramDragOffsetY;
        this.noteOnTop = false;
        return true;
    }

    /**
     * 鼠标是否落在主图解板上（含左侧滑出的配置纸——它与主板绑定，一并移动）。
     */
    private boolean overDiagramBoard(final double mouseX, final double mouseY) {
        final int boardX = this.getDiagramX();
        final int boardY = this.getDiagramY();

        if (mouseX >= boardX && mouseX < boardX + DIAGRAM_TEXTURE.width
                && mouseY >= boardY && mouseY < boardY + DIAGRAM_TEXTURE.height) {
            return true;
        }

        // 配置纸只在滑出状态下参与命中判定。
        final float paperOffset = this.getPaperOffset(1.0f);
        if (paperOffset <= MIN_PAPER_OFFSET + 0.5f) {
            return false;
        }

        final int paperX = (int) (boardX - paperOffset);
        return mouseX >= paperX && mouseX < paperX + SimGUITextures.DIAGRAM_PAPER.width
                && mouseY >= boardY && mouseY < boardY + SimGUITextures.DIAGRAM_PAPER.height;
    }

    private void updateNote(final double mouseX, final double mouseY, final boolean widgetRelease) {
        if (MAGNIFYING_CENTER.distanceSquared(MAGNIFYING_MAX) < MIN_MAGNIFICATION_PIXELS * MIN_MAGNIFICATION_PIXELS) {
            return;
        }

        this.updateMagnificationBox(mouseX, mouseY);

        if (this.note.contains(MAGNIFYING_CENTER.x, MAGNIFYING_CENTER.y)) {
            if (this.pointsWithinNote(MAGNIFYING_MAX, MAGNIFYING_MIN)) {
                this.note.handleInternalUpdate(MAGNIFYING_MAX, MAGNIFYING_MIN);

                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 1.0f));
            }

            return;
        }

        if (!this.pointsWithinDiagram(MAGNIFYING_MAX, MAGNIFYING_MIN) || widgetRelease /*|| !this.isMagnifying()*/) {
            return;
        }

        final int diagramX = this.getDiagramX();
        final int diagramY = this.getDiagramY();

        this.config.getNoteConfigs().setNoteYaw(this.config.yaw());
        this.config.getNoteConfigs().setNotePitch(this.config.pitch());
        this.config.getNoteConfigs().setActive(true);

        // [1.20.1 移植修复] 主图相机在渲染空间，便签内部在 plot 空间，
        // 反投影前先把相机对齐到 plot 空间，否则 noteScope 坐标空间错误 → 便签显示空白。
        this.note.updateCurrentScope(MAGNIFYING_MAX.sub(diagramX, diagramY, new Vector2d()), MAGNIFYING_MIN.sub(diagramX, diagramY, new Vector2d()), this.sable$plotSpaceCamera(LOCAL_CAMERA_POSITION), PROJECTION_MAT);
        this.note.activate();

        this.setConfigDirty();
        this.magnifying = false;

        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 1.0f));
    }

    /**
     * Updates the magnification box min/max from the given mouse x/y.
     */
    private void updateMagnificationBox(final double mouseX, final double mouseY) {
        MAGNIFYING_MAX.set(mouseX, mouseY);

        MAGNIFYING_MAX.sub(MAGNIFYING_CENTER);
        MAGNIFYING_MAX.absolute();

        final double max = Math.max(MAGNIFYING_MAX.x, MAGNIFYING_MAX.y);
        MAGNIFYING_MAX.set(max, max);

        MAGNIFYING_MAX.negate(MAGNIFYING_MIN).add(MAGNIFYING_CENTER);
        MAGNIFYING_MAX.add(MAGNIFYING_CENTER);
    }

    @Override
    protected void renderWindow(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTicks) {
        final PoseStack ps = graphics.pose();

        if (this.subLevel.isRemoved() || this.diagram.isRemoved()) {
            this.onClose();
            return;
        }

        this.renderContents(this.subLevel, partialTicks);

        // [1.20.1 移植·修复配置纸选项卡频闪] 3D 离屏渲染会留下开启的深度测试，必须在此显式关闭，
        // 确保后续所有 GUI（配置纸选项卡、主图解板、受力箭头等）按绘制顺序层叠、
        // 不被主帧缓冲里残留的世界深度随机剔除。DiagramOutlinePostProcess 已还原此状态，
        // 这里再兜底一次，覆盖所有调用/降级路径。
        RenderSystem.disableDepthTest();

        // [1.20.1 移植·图纸自由摆放] 便签默认压在最底层：在配置纸、主图解板、按钮之前先画，
        // 于是会被它们盖住。只有当便签被右键拖拽提到最上层时，才改到前景阶段绘制。
        if (!this.noteOnTop) {
            this.note.renderWidget(graphics, mouseX, mouseY, partialTicks);
        }

        // genuinely how can this be null they are assigned values in the constructor
        if (this.turnDownButton != null && this.turnUpButton != null) {
            this.turnDownButton.visible = this.turnDownButton.active = this.config.pitch() < 45.0f;
            this.turnUpButton.visible = this.turnUpButton.active = this.config.pitch() > -45.0f;
        }

        ps.pushPose();

        for (final DiagramForceGroupToggle widget : this.forceToggleWidgets) {
            widget.active = this.paperVisible;
            widget.updateForceState(this.serverData);
            widget.renderTab(graphics, mouseX, mouseY, partialTicks);
        }

        final int diagramX = this.getDiagramX();
        final int diagramY = this.getDiagramY();

        // Render config paper
        ps.pushPose();
        ps.translate(diagramX, diagramY, 0);
        ps.translate(-this.getPaperOffset(partialTicks), 0, 0.0f);
        SimGUITextures.DIAGRAM_PAPER.render(graphics, 0, 0);
        ps.popPose();

        for (final DiagramForceGroupToggle widget : this.forceToggleWidgets) {
            widget.render(graphics, mouseX, mouseY, partialTicks);
        }

        // Render diagram
        ps.translate(diagramX, diagramY, 0);

        // Main background
        DIAGRAM_TEXTURE.render(graphics, 0, 0);

        renderFBO(graphics, this.finalFbo, DIAGRAM_TEXTURE.width, DIAGRAM_TEXTURE.height);

        final String text = this.subLevel.getName();

        ps.pushPose();
        ps.translate(0, 0, 1);
        if (text != null && !text.isEmpty()) {
            final int footerW = this.font.width(text);
            graphics.fill(DIAGRAM_TEXTURE.width - footerW - 7, DIAGRAM_TEXTURE.height - 5 - this.font.lineHeight, DIAGRAM_TEXTURE.width - 4, DIAGRAM_TEXTURE.height - 3, BG_COLOR.getRGB());
            graphics.drawString(this.font, text, DIAGRAM_TEXTURE.width - footerW - 5, DIAGRAM_TEXTURE.height - 3 - this.font.lineHeight, TEXT_COLOR.getRGB(), false);
        }
        ps.popPose();

        this.renderArrows(graphics,
                mouseX,
                mouseY,
                diagramX,
                diagramY,
                LOCAL_ORIENTATION,
                LOCAL_CAMERA_POSITION,
                PROJECTION_MAT,
                DIAGRAM_TEXTURE.width,
                DIAGRAM_TEXTURE.height);

        if (this.config.displayCenterOfMass()) {
            this.renderCenterOfMass(graphics);
        }

        ps.popPose();

    }

    @Override
    protected void renderWindowForeground(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTicks) {
        // [1.20.1 移植·图纸自由摆放] 便签被右键拖到最上层时，在前景阶段绘制：
        // 此时主图解贴屏与全部按钮控件均已画完，便签得以盖住它们；
        // 提示框（tooltip）仍画在便签之上，保持可读。
        if (this.noteOnTop) {
            this.note.renderWidget(graphics, mouseX, mouseY, partialTicks);
        }

        final PoseStack ps = graphics.pose();

        this.renderMagnificationHighlight(graphics, mouseX, mouseY, ps);

        if (!this.tooltipList.isEmpty()) {
            DiagramScreen.renderTooltip(graphics, mouseX, mouseY, this.tooltipList);
        }

        this.tooltipList.clear();

        super.renderWindowForeground(graphics, mouseX, mouseY, partialTicks);
    }

    private void renderMagnificationHighlight(final GuiGraphics graphics, final int mouseX, final int mouseY, final PoseStack ps) {
        final boolean initiallyWithinNote = this.note.contains(MAGNIFYING_CENTER.x, MAGNIFYING_CENTER.y);

        this.updateMagnificationBox(mouseX, mouseY);

        if (MAGNIFYING_CENTER.distanceSquared(MAGNIFYING_MAX) < MIN_MAGNIFICATION_PIXELS * MIN_MAGNIFICATION_PIXELS) {
            return;
        }

        if (initiallyWithinNote || (/*this.isMagnifying() && */this.contains(MAGNIFYING_CENTER.x, MAGNIFYING_CENTER.y))) {
            ps.pushPose();
            ps.translate(0, 0, 1);

            final Vector2d min = new Vector2d(MAGNIFYING_MIN);
            final Vector2d max = new Vector2d(MAGNIFYING_MAX);

            final boolean valid = initiallyWithinNote ?
                    (this.note.contains(min.x, min.y) && this.note.contains(max.x, max.y)) :
                    (this.contains(min.x, min.y) && this.contains(max.x, max.y));

            if (initiallyWithinNote) {
                this.note.clamp(min);
                this.note.clamp(max);
            } else {
                this.clamp(min);
                this.clamp(max);
            }

            final double startX = min.x;
            final double startY = min.y;
            final double endX = max.x;
            final double endY = max.y;
            final int fillColor = valid ? 0x40fffcfc : 0x40aaaaaa;
            final int color = valid ? 0x90ffffff : 0x90ffaaaa;

            graphics.fill((int) startX, (int) startY, (int) endX, (int) endY, fillColor);
            graphics.hLine((int) startX, (int) endX, (int) startY, color);
            graphics.hLine((int) startX, (int) endX, (int) endY, color);
            graphics.vLine((int) startX, (int) startY, (int) endY, color);
            graphics.vLine((int) endX, (int) startY, (int) endY, color);

            ps.popPose();
        }
    }

    public boolean pointsWithinNote(final Vector2d target, final Vector2d inverse) {
        return this.note.contains(target.x, target.y) && this.note.contains(inverse.x, inverse.y);
    }

    public boolean pointsWithinDiagram(final Vector2d target, final Vector2d inverse) {
        return this.contains(target.x, target.y) && this.contains(inverse.x, inverse.y);
    }

    public boolean contains(double x, double y) {
        // [1.20.1 移植·图纸自由摆放] 改用主图解板的真实屏幕坐标（含右移偏置与拖拽偏移），
        // 顺带修正原先只按屏幕居中计算、漏算右移偏置导致的框选命中区偏移问题。
        x -= this.getDiagramX();
        y -= this.getDiagramY();

        return x > 0 && x < DIAGRAM_TEXTURE.width && y > 0 && y < DIAGRAM_TEXTURE.height;
    }

    public Vector2d clamp(final Vector2d dest) {
        final float minX = this.getDiagramX();
        final float minY = this.getDiagramY();
        dest.max(new Vector2d(minX, minY));
        dest.min(new Vector2d(minX + DIAGRAM_TEXTURE.width - 1, minY + DIAGRAM_TEXTURE.height - 1));
        return dest;
    }

    public static void renderFBO(final GuiGraphics graphics, final AdvancedFbo fbo, final int width, final int height) {
        final int id = fbo.getColorTextureAttachment(0);

        RenderSystem.setShaderTexture(0, id);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        final Matrix4f matrix4f = graphics.pose().last().pose();
        final BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        final float x1 = 0.0f;
        final float y1 = 0.0f;
        bufferbuilder.vertex(matrix4f, x1, y1, 0.0f).uv(0.0f, 1.0f).color(255, 255, 255, 255).endVertex();
        bufferbuilder.vertex(matrix4f, x1, height, 0.0f).uv(0.0f, 0.0f).color(255, 255, 255, 255).endVertex();
        bufferbuilder.vertex(matrix4f, width, height, 0.0f).uv(1.0f, 0.0f).color(255, 255, 255, 255).endVertex();
        bufferbuilder.vertex(matrix4f, width, y1, 0.0f).uv(1.0f, 1.0f).color(255, 255, 255, 255).endVertex();
        BufferUploader.drawWithShader(bufferbuilder.end());
        RenderSystem.disableBlend();
    }

    public void renderArrows(final GuiGraphics graphics,
                             final int mouseX,
                             final int mouseY,
                             final int areaOriginX,
                             final int areaOriginY,
                             final Quaternionfc orientation,
                             final Vector3dc cameraPos,
                             final Matrix4fc projMatrix,
                             final int areaWidth,
                             final int areaHeight) {
        if (this.serverData != null) {
            // Record max arrow length
            double maxArrowLengthSquared = 0.0;

            final Map<ForceGroup, List<ForceClusterFinder.Cluster>> clusters = new HashMap<>();

            for (final ResourceLocation groupId : this.config.enabledForceGroups()) {
                final ForceGroup group = ForceGroups.REGISTRY.get(groupId);
                assert group != null;

                final List<QueuedForceGroup.PointForce> forces = this.serverData.forces().get(group);

                if (forces == null) continue;
                final List<ForceClusterFinder.Cluster> cluster = this.config.mergeForces() ? ForceClusterFinder.getMergedClusters(forces) : ForceClusterFinder.passThrough(forces);

                clusters.put(group, cluster);

                for (final ForceClusterFinder.Cluster force : cluster) {
                    maxArrowLengthSquared = Math.max(maxArrowLengthSquared, force.force().lengthSquared());
                }
            }

            for (final ResourceLocation groupId : this.config.enabledForceGroups()) {
                final ForceGroup group = ForceGroups.REGISTRY.get(groupId);
                assert group != null;

                final List<ForceClusterFinder.Cluster> cluster = clusters.get(group);

                if (cluster == null) continue;

                for (final ForceClusterFinder.Cluster force : cluster) {
                    this.renderForceArrow(graphics,
                            group,
                            force,
                            Math.sqrt(maxArrowLengthSquared),
                            mouseX - areaOriginX,
                            mouseY - areaOriginY,
                            this.tooltipList,
                            orientation,
                            cameraPos,
                            projMatrix,
                            areaWidth,
                            areaHeight);
                }
            }
        }
    }

    /**
     * [1.20.1 移植修复·受力箭头坐标空间对齐]
     *
     * <p>源版（1.21.1）里离屏相机与受力点同处 <b>plot 局部空间</b>（数值约个位数），
     * 所以受力点可以直接投影。本端口为修「物理化后蓝图空白」，已把离屏相机改成
     * {@code subLevel.boundingBox()}（sweptBounds）的中心，即<b>渲染/世界空间</b>（数值约几百）；
     * 而服务端记录的受力点仍然是 <b>plot 世界空间</b>（子关卡方块实际存放的远端坐标，约 2048 万）。
     * 两者不同空间 → 投影出的屏幕坐标高达上亿像素，箭头全部落在图纸之外，表现为「受力看不见」。</p>
     *
     * <p>此方法把点统一搬到「与离屏相机相同」的空间：候选一为原样（点本就在渲染空间），
     * 候选二为经 {@code renderPose} 变换（plot → 渲染空间）。取离相机更近的那个，
     * 从而同时兼容物理化 / 未物理化两种状态，无需判断当前是否已起飞。</p>
     *
     * @param point     待投影的点（plot 空间或渲染空间均可）
     * @param cameraPos 离屏相机位置（渲染空间）
     * @return 与相机同空间的点
     */
    private Vector3d sable$alignToCameraSpace(final Vector3dc point, final Vector3dc cameraPos) {
        final Vector3d raw = new Vector3d(point);
        final Pose3dc pose = this.subLevel.renderPose();
        if (pose == null) {
            return raw;
        }

        final Vector3d transformed = pose.transformPosition(point, new Vector3d());

        // 距离相机更近的即为正确空间（另一空间会相差上千万格）
        return transformed.distanceSquared(cameraPos) <= raw.distanceSquared(cameraPos) ? transformed : raw;
    }

    /**
     * [1.20.1 移植修复·便签放大镜选区空间对齐]
     *
     * <p>主图的离屏相机 {@code LOCAL_CAMERA_POSITION} 现在位于<b>渲染/世界空间</b>，
     * 而便签内部（{@code DiagramStickyNote.populateFBO} / {@code handleInternalUpdate}）
     * 一直使用 <b>plot 空间</b>相机 {@code NOTE_LOCAL_CAM_POS}。当玩家在主图上框选放大镜区域时，
     * {@code updateCurrentScope} 会把屏幕坐标反投影成 3D 坐标并存入 {@code noteScope}；
     * 若此时用渲染空间相机做反投影，得到的是渲染空间坐标（约 383），而便签后续却按 plot 空间
     * 去解释它 → 范围完全对不上 → 便签空白。</p>
     *
     * <p>此方法把渲染空间相机转回 plot 空间（{@code transformPositionInverse}），
     * 并用"哪个结果更接近 plot 局部中心"做判据，防止未来相机改回 plot 空间时产生双重变换。</p>
     *
     * @param cameraPos 当前主图离屏相机位置
     * @return 与便签内部一致的 plot 空间相机位置
     */
    private Vector3d sable$plotSpaceCamera(final Vector3dc cameraPos) {
        final Pose3dc pose = this.subLevel.renderPose();
        if (pose == null) {
            return new Vector3d(cameraPos);
        }

        final BoundingBox3ic plotBounds = this.subLevel.getPlot().getBoundingBox();
        final Vector3d plotCenter = new Vector3d(
                (plotBounds.minX() + plotBounds.maxX() + 1) / 2.0,
                (plotBounds.minY() + plotBounds.maxY() + 1) / 2.0,
                (plotBounds.minZ() + plotBounds.maxZ() + 1) / 2.0
        );

        final Vector3d raw = new Vector3d(cameraPos);
        final Vector3d inverse = pose.transformPositionInverse(cameraPos, new Vector3d());

        // plot 空间相机应接近 plot 局部中心；渲染空间相机则远离它
        return inverse.distanceSquared(plotCenter) <= raw.distanceSquared(plotCenter) ? inverse : raw;
    }

    /**
     * Renders a force arrow for a given point force and force group
     */
    private void renderForceArrow(final GuiGraphics graphics,
                                  final ForceGroup forceGroup,
                                  final ForceClusterFinder.Cluster pointForce,
                                  final double maxArrowLength,
                                  final int mouseX,
                                  final int mouseY,
                                  final List<FormattedText> tooltipLines,
                                  final Quaternionfc orientation,
                                  final Vector3dc cameraPos,
                                  final Matrix4fc projMatrix,
                                  final int areaWidth,
                                  final int areaHeight) {
        final double forceMagnitude = pointForce.force().length();

        if (forceMagnitude <= 0.01 || this.viewportRadius == 0.0) {
            return;
        }

        final Vector3d globalFirstDir = pointForce.force().normalize(new Vector3d());
        final Vector3d forceOffset = globalFirstDir.mul(Math.max(0.25, forceMagnitude / maxArrowLength) * this.viewportRadius * 0.5, new Vector3d());

        // [1.20.1 移植修复] 先把受力点搬到离屏相机所在空间，再投影（详见 sable$alignToCameraSpace 注释）
        final Vector3d originPoint = this.sable$alignToCameraSpace(pointForce.pos(), cameraPos);

        final Vector2d originCoords = getScreenCoords(new Vector3d(originPoint), orientation, cameraPos, projMatrix, areaWidth, areaHeight);
        if (!this.canDrawArrowAt((int) originCoords.x, (int) originCoords.y, areaWidth, areaHeight)) {
            return;
        }

        final Vector2d mousePos = new Vector2d(mouseX, mouseY);

        final int color = (255 << 24) | forceGroup.color();
        final int shadowColor = 0xfff9f2de;

        final double facingDot = orientation.transformInverse(globalFirstDir, new Vector3d()).dot(OrientedBoundingBox3d.FORWARD);

        if (Math.abs(facingDot) > 0.85) {
            final PoseStack ps = graphics.pose();
            ps.pushPose();

            ps.translate(0.0, 0.0, 1.0f);

            // tooltip time!
            if (mousePos.sub(originCoords, new Vector2d()).lengthSquared() < 8.0 * 8.0) {
                addForceArrowTooltip(forceGroup, pointForce.groupSize().getValue(), forceMagnitude, color, tooltipLines);
            }

            if (facingDot < 0.0) {
                SimGUITextures.DIAGRAM_ICON_ARROW_IN_PAGE_SHADOW.render(graphics, (int) originCoords.x - 8, (int) originCoords.y - 8, new Color(shadowColor));
                SimGUITextures.DIAGRAM_ICON_ARROW_IN_PAGE.render(graphics, (int) originCoords.x - 8, (int) originCoords.y - 8, new Color(color));
            } else {
                SimGUITextures.DIAGRAM_ICON_ARROW_OUT_PAGE_SHADOW.render(graphics, (int) originCoords.x - 8, (int) originCoords.y - 8, new Color(shadowColor));
                SimGUITextures.DIAGRAM_ICON_ARROW_OUT_PAGE.render(graphics, (int) originCoords.x - 8, (int) originCoords.y - 8, new Color(color));
            }

            ps.popPose();
            return;
        }

        // [1.20.1 移植修复] 箭头末端在「与相机同空间」的起点上叠加力偏移，
        // forceOffset 的长度由 viewportRadius 决定（本就是渲染空间尺度），故在此空间相加才不会被 plot 缩放带偏。
        final Vector2d resultCoords = getScreenCoords(originPoint.add(forceOffset, new Vector3d()), orientation, cameraPos, projMatrix, areaWidth, areaHeight);

        final Vector2d arrowDir = resultCoords.sub(originCoords, new Vector2d());
        float arrowLength = (float) arrowDir.length();
        arrowDir.div(arrowLength);

        while (arrowLength > 0 && !this.canDrawArrowAt((int) resultCoords.x, (int) resultCoords.y, areaWidth, areaHeight)) {
            resultCoords.fma(-3.0, arrowDir);
            arrowLength -= 3.0f;
        }

        final int x1 = (int) originCoords.x();
        final int y1 = (int) originCoords.y();

        final int x2 = (int) resultCoords.x();
        final int y2 = (int) resultCoords.y();

        final MultiBufferSource.BufferSource bufferSource = graphics.bufferSource();
        final VertexConsumer builder = bufferSource.getBuffer(RenderType.gui());
        final Matrix4f pose = graphics.pose().last().pose();

        final Vector2d arrowLeft = new Vector2d(-arrowDir.y(), arrowDir.x()).mul(4.0);
        final Vector2d arrowRight = new Vector2d(arrowDir.y(), -arrowDir.x()).mul(4.0);

        final float headLen = 6.0f;

        final boolean drawArrow = originCoords.distanceSquared(resultCoords) > MIN_ARROW_SIZE_PX * MIN_ARROW_SIZE_PX;

        double distanceAlongLine = mousePos.sub(originCoords, new Vector2d()).dot(arrowDir);
        distanceAlongLine = Mth.clamp(distanceAlongLine, 0.0, arrowLength);

        final boolean displayTooltip = new Vector2d(originCoords).fma(distanceAlongLine, arrowDir).distance(mousePos) < 5.0;
        if (displayTooltip) {
            // tooltip time!
            addForceArrowTooltip(forceGroup, pointForce.groupSize().getValue(), forceMagnitude, color, tooltipLines);
        }

        // Draw base dot
        final int z = 1;
        int inflation = 3;
        builder.vertex(pose, (float) x1 - inflation, (float) y1 - inflation, (float) z).color(shadowColor).endVertex();
        builder.vertex(pose, (float) x1 - inflation, (float) y1 + 1 + inflation, (float) z).color(shadowColor).endVertex();
        builder.vertex(pose, (float) x1 + 1 + inflation, (float) y1 + 1 + inflation, (float) z).color(shadowColor).endVertex();
        builder.vertex(pose, (float) x1 + 1 + inflation, (float) y1 - inflation, (float) z).color(shadowColor).endVertex();

        if (drawArrow) {
            // Arrow shadow
            drawLine(builder, pose, x2, y2, (int) (x2 - arrowDir.x * headLen + arrowLeft.x), (int) (y2 - arrowDir.y * headLen + arrowLeft.y), shadowColor, 1);
            drawLine(builder, pose, x2, y2, (int) (x2 - arrowDir.x * headLen + arrowRight.x), (int) (y2 - arrowDir.y * headLen + arrowRight.y), shadowColor, 1);
            drawLine(builder, pose, x1, y1, x2, y2, shadowColor, 1);

            // Actual arrow
            drawLine(builder, pose, x2, y2, (int) (x2 - arrowDir.x * headLen + arrowLeft.x), (int) (y2 - arrowDir.y * headLen + arrowLeft.y), color, 0);
            drawLine(builder, pose, x2, y2, (int) (x2 - arrowDir.x * headLen + arrowRight.x), (int) (y2 - arrowDir.y * headLen + arrowRight.y), color, 0);
            drawLine(builder, pose, x1, y1, x2, y2, color, 0);
        }

        inflation = 2;
        builder.vertex(pose, (float) x1 - inflation, (float) y1 - inflation, (float) z).color(color).endVertex();
        builder.vertex(pose, (float) x1 - inflation, (float) y1 + 1 + inflation, (float) z).color(color).endVertex();
        builder.vertex(pose, (float) x1 + 1 + inflation, (float) y1 + 1 + inflation, (float) z).color(color).endVertex();
        builder.vertex(pose, (float) x1 + 1 + inflation, (float) y1 - inflation, (float) z).color(color).endVertex();
    }

    private static void addForceArrowTooltip(final ForceGroup forceGroup, final int forceCount, final double forceMagnitude, final int color, final List<FormattedText> tooltipLines) {
        final LangBuilder forceNameText = SimLang.builder().add(forceGroup.name()).color(color);
        final LangBuilder forceMagnitudeText = SimLang.translate("contraption_diagram.force_arrow_magnitude", String.format("%,.2f", forceMagnitude)).color(0xffffffff);

        if (forceCount > 1)
            tooltipLines.add(SimLang.translate("contraption_diagram.merged_force_arrow", SimLang.translate("contraption_diagram.merging_numeral", Integer.toString(forceCount)).color(0xffffffff), forceNameText, forceMagnitudeText).color(TOOLTIP_LABEL_COLOR).component());
        else
            tooltipLines.add(SimLang.translate("contraption_diagram.force_arrow", forceNameText, forceMagnitudeText).color(TOOLTIP_LABEL_COLOR).component());
    }

    private boolean canDrawArrowAt(final int x, final int y, final int width, final int height) {
        final int padding = 8;
        return x >= padding && x < width - padding && y >= padding && y < height - padding;
    }

    private static void drawLine(final VertexConsumer builder, final Matrix4f pose, int x1, int y1, final int x2, final int y2, final int color, final int inflation) {
        // don't miss none of them pixels! you heard me!
        final int z = 1;
        final int dx = Math.abs(x2 - x1);
        final int dy = Math.abs(y2 - y1);
        final int sx = x1 < x2 ? 1 : -1;
        final int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            builder.vertex(pose, (float) x1 - inflation, (float) y1 - inflation, (float) z).color(color).endVertex();
            builder.vertex(pose, (float) x1 - inflation, (float) y1 + 1 + inflation, (float) z).color(color).endVertex();
            builder.vertex(pose, (float) x1 + 1 + inflation, (float) y1 + 1 + inflation, (float) z).color(color).endVertex();
            builder.vertex(pose, (float) x1 + 1 + inflation, (float) y1 - inflation, (float) z).color(color).endVertex();

            if (x1 == x2 && y1 == y2) break;

            final int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }

    private SimGUITextures getMergeIcon() {
        return this.config.mergeForces() ? SimGUITextures.DIAGRAM_ICON_FORCES_MERGED : SimGUITextures.DIAGRAM_ICON_FORCES_SEPARATED;
    }

    public float getPaperOffset(final float partialTicks) {
        return Mth.lerp(partialTicks, this.lastPaperOffset, this.paperOffset);
    }

    public float getTabOffset(final float partialTicks) {
        return Mth.lerp(partialTicks, this.lastTabOffset, this.tabOffset);
    }

    private void renderCenterOfMass(final GuiGraphics graphics) {
        // [1.20.1 移植修复] 质心（rotationPoint）同样记录在 plot 空间，需先对齐到离屏相机空间再投影，
        // 否则与受力箭头一样被投到图纸之外（详见 sable$alignToCameraSpace 注释）
        final Vector3d centerOfMass = this.sable$alignToCameraSpace(this.subLevel.logicalPose().rotationPoint(), LOCAL_CAMERA_POSITION);
        final Vector2d screenCoords = getScreenCoords(centerOfMass, LOCAL_ORIENTATION, LOCAL_CAMERA_POSITION, PROJECTION_MAT, DIAGRAM_TEXTURE.width, DIAGRAM_TEXTURE.height);

        final SimGUITextures tex = SimGUITextures.DIAGRAM_ICON_COM;

        final PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(screenCoords.x - 8, screenCoords.y - 8, 0);
        graphics.blit(tex.location, 0, 0, 5, tex.startX, tex.startY, tex.width, tex.height, tex.texWidth, tex.texHeight);
        pose.popPose();
    }

    /**
     * Projects a 3D point into pixel coordinates
     *
     * @param plotSpacePoint the point to project
     * @param localPosition  the position used to project
     * @param projMatrix     the projection matrix
     * @return pixel coordinates in diagram-space
     */
    public static Vector2d getScreenCoords(final Vector3d plotSpacePoint, final Quaternionfc orientation, final Vector3dc localPosition, final Matrix4fc projMatrix, final int width, final int height) {
        plotSpacePoint.sub(localPosition);
        orientation.transformInverse(plotSpacePoint);

        final Vector4f clipSpace = new Vector4f((float) plotSpacePoint.x, (float) plotSpacePoint.y, (float) plotSpacePoint.z, 1.0f);
        clipSpace.mul(projMatrix);
        clipSpace.div(clipSpace.w);

        final double projectedX = ((clipSpace.x() * 0.5f + 0.5f) * width);
        final double projectedY = ((-clipSpace.y() * 0.5f + 0.5f) * height);
        return new Vector2d(projectedX, projectedY);
    }

    /**
     * Projects a diagram-space coordinate into plot-space
     *
     * @param diagramSpacePoint the point to project
     * @param localPosition     the position used to project
     * @param projMatrix        the projection matrix
     * @return 3D point in plot-space
     */
    public static Vector3d getPlotCoords(final Vector2dc diagramSpacePoint, final Quaternionfc orientation, final Vector3dc localPosition, final Matrix4fc projMatrix, final int width, final int height) {
        final Vector3d clipSpace = new Vector3d(2 * diagramSpacePoint.x() / width - 1, 1 - 2 * diagramSpacePoint.y() / height, 0);
        final Vector3d point = clipSpace.sub(projMatrix.getTranslation(new Vector3f())).div(projMatrix.m00(), projMatrix.m11(), projMatrix.m22());

        orientation.transform(point);
        point.add(localPosition);
        return point;
    }

    public void updateData(final DiagramDataPacket data) {
        this.serverData = data;
    }

    public static void renderTooltip(final GuiGraphics guiGraphics, final int x, final int y, final List<FormattedText> lines) {
        final Font font = Minecraft.getInstance().font;

        final Color colorBackground = new Color(0xff3d322a);
        final Color colorBorderTop = new Color(0xff5d483a);
        final Color colorBorderBot = new Color(0xff5d483a);
        RemovedGuiUtils.drawHoveringText(guiGraphics, lines, x, y, guiGraphics.guiWidth(), guiGraphics.guiHeight(), -1, colorBackground.getRGB(), colorBorderTop.getRGB(), colorBorderBot.getRGB(), font);
    }

    public void setConfigDirty() {
        this.configDirty = true;
    }

//    public boolean isMagnifying() {
//        return true;
//    }

    /**
     * [1.20.1 移植·图纸自由摆放]
     * x / y 现为相对主图解板左上角的坐标，绘制时叠加图解板当前屏幕位置，
     * 从而随图解板右键拖拽一起平移。
     */
    public record GreebleRenderable(DiagramScreen screen, int x, int y, int width, int height,
                                    ResourceLocation texture,
                                    Greeble.TextureSlice slice) implements Renderable {
        @Override
        public void render(final GuiGraphics guiGraphics, final int i, final int i1, final float v) {
            guiGraphics.blit(this.texture, this.x + this.screen.getDiagramX(), this.y + this.screen.getDiagramY(), this.slice.x(), this.slice.y(), this.slice.width(), this.slice.height(), this.width, this.height);
        }
    }
}
