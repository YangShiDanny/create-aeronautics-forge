package dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.equipment.clipboard.ClipboardCloneable;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.LinkedTypewriterMenuCommon;
import dev.simulated_team.simulated.data.SimLang;
import dev.simulated_team.simulated.index.SimBlocks;
import dev.simulated_team.simulated.index.SimSoundEvents;
import dev.simulated_team.simulated.mixin_interface.PlayerTypewriterExtension;
import dev.simulated_team.simulated.service.SimPlatformService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import dev.simulated_team.simulated.libs.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraftforge.common.ForgeMod;

public class LinkedTypewriterBlockEntity extends SmartBlockEntity implements MenuProvider, ClipboardCloneable {
    private LinkedTypewriterEntries entryMap;
    private ListTag pendingKeysTag = null;
    private final List<Integer> pressedKeys = new ArrayList<>();
    private UUID currentUser;
    private String typedEntry = "";

    public boolean powered;
    public LinkedTypewriterBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
        this.entryMap = new LinkedTypewriterEntries();
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> list) {
    }

    @Override
    public void tick() {
        super.tick();

        assert this.getLevel() != null;
        this.entryMap.updateNetworks(this.getLevel());

        if (!this.getLevel().isClientSide) {
            if (this.getBlockState().getValue(LinkedTypewriterBlock.POWERED) != this.powered) {
                this.getLevel().setBlockAndUpdate(this.getBlockPos(), this.getBlockState().setValue(LinkedTypewriterBlock.POWERED, this.powered));
            }

            if (this.currentUser != null) {
                final Player currentPlayer = this.getLevel().getPlayerByUUID(this.currentUser);
                if (currentPlayer == null || !playerInRange(currentPlayer, this.getLevel(), this.getBlockPos())) {
                    this.disconnectUser();
                }
            }
        }
    }

    public static boolean playerInRange(final Player player, final Level world, final BlockPos pos) {
        final double range = player.getAttribute(ForgeMod.BLOCK_REACH.get()).getValue();

        // Make sure we take into account sub-levels! We are a sable addon after all!
        return Sable.HELPER.distanceSquaredWithSubLevels(world, player.getEyePosition(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < range * range;
    }

    public LinkedTypewriterEntries getTypewriterEntries() {
        return this.entryMap;
    }

    /**
     * Attempts to start using this typewriter if there is not already another user interacting with it. <br>
     * Disconnects the previous typewriter if the given user starts using a new one.
     *
     * @param userID The user interacting with this typewriter
     * @return Whether the user was able to successfully start using this typewriter
     */
    public boolean checkAndStartUsing(final UUID userID) {
        if (this.currentUser == null) {
            final Player player = this.getLevel().getPlayerByUUID(userID);
            if (player != null) {
                final PlayerTypewriterExtension playerEx = (PlayerTypewriterExtension) player;
                this.currentUser = userID;
                final BlockPos previousTypewriter = playerEx.simulated$getCurrentTypewriter();
                if (previousTypewriter != null) {
                    if (this.getLevel().getBlockEntity(previousTypewriter) instanceof final LinkedTypewriterBlockEntity nbe && nbe != this) {
                        nbe.disconnectUser();
                    }
                }

                this.powered = true;
                playerEx.simulated$setCurrentTypewriter(this.getBlockPos());
                if (this.getLevel().isClientSide) {
                    LinkedTypewriterInteractionHandler.associateTypewriter(this);
                } else {
                    this.getLevel().playSound(null, this.worldPosition, AllSoundEvents.CONTROLLER_PUT.getMainEvent(), SoundSource.BLOCKS, 1.0F, 0.95F + 0.1F * this.getLevel().getRandom().nextFloat());
                    this.sendConnectMessage(player);
                }

                return true;
            }
        }

        return false;
    }

    public void sendConnectMessage(final Player player) {
        final Component customName = this.getDisplayName() != null ? this.getDisplayName() : SimLang.translate("linked_typewriter.title").component();
        player.displayClientMessage(SimLang.translate("linked_typewriter.start_controlling", customName.getString()).component(), true);
    }

    public void sendDisconnectMessage(final Player player) {
        final Component customName = this.getDisplayName() != null ? this.getDisplayName() : SimLang.translate("linked_typewriter.title").component();
        player.displayClientMessage(SimLang.translate("linked_typewriter.stop_controlling", customName.getString()).component(), true);
    }

    public boolean checkUser(final UUID user) {
        return user.equals(this.currentUser);
    }

    public boolean isInUse() {
        return this.currentUser != null;
    }

    /**
     * Disconnects the current user.
     */
    public void disconnectUser() {
        if (!this.getLevel().isClientSide) {
            this.pressedKeys.clear();
            this.entryMap.deactivateAll();
            this.setChanged();
            this.sendData();

            this.getLevel().playSound(null, this.worldPosition, SimSoundEvents.LINKED_TYPEWRITER_DING.event(), SoundSource.BLOCKS, 1.0F, 0.95F + 0.1F * this.getLevel().getRandom().nextFloat());
            final Player player = this.getLevel().getPlayerByUUID(this.currentUser);
            if (player != null) {
                this.sendDisconnectMessage(player);
                ((PlayerTypewriterExtension)player).simulated$setCurrentTypewriter(null);
            }
        } else {
            LinkedTypewriterInteractionHandler.associateTypewriter(null);
        }

        this.powered = false;
        this.currentUser = null;
    }

    public List<Integer> getPressedKeys() {
        return this.pressedKeys;
    }

    /**
     * Called whenever a key is interacted with. Used to bind a frequency to a key, or press / release the given key
     *
     * @param user   The user attempting to interact with this typewwriter
     * @param toBind The frequency to bind to this key. Nullable
     * @param key    They key to interact with
     * @param press  Whether this action is a press or release action
     */
    public void onKeyInteraction(final UUID user,  final LinkedTypewriterEntries.KeyboardEntry toBind, final int key, final boolean press) {
        if (!this.checkUser(user)) {
            return;
        }

        if (press && toBind != null) {
            this.entryMap.setKey(key, toBind);
            return;
        }

        if (press) {
            this.pressKey(key);
        } else {
            this.releaseKey(key);
        }
    }

    /**
     * @param key the key to activate
     */
    public void pressKey(final int key) {
        this.pressedKeys.add(key);
        if (key == 259) {
            if (!this.typedEntry.isEmpty()) {
                this.typedEntry = this.typedEntry.substring(0, this.typedEntry.length() - 1);
            }
        } else {
            this.typedEntry = this.typedEntry + ((char) key);
        }
        if (this.typedEntry.length() >= 25) {
            this.typedEntry = this.typedEntry.substring(1);
        }
        this.entryMap.activateKey(key, this);
    }

    /**
     * @param key The key to deactivate
     */
    public void releaseKey(final int key) {
        this.pressedKeys.remove((Integer) key);
        this.entryMap.deactivateKey(key);
    }

    protected void write(final CompoundTag tag, final boolean clientPacket) {
        super.write(tag, clientPacket);

        tag.putString("typedEntry", this.typedEntry);
        tag.put("Keys", this.entryMap.saveKeys(this.getLevel().registryAccess()));

        if (this.currentUser != null) {
            tag.putUUID("CurrentUser", this.currentUser);
        }
    }

    protected void read(final CompoundTag tag, final boolean clientPacket) {
        super.read(tag, clientPacket);

        // [1.20.1 移植修正] 子层级加载时 getLevel() 尚为 null，readKeys 需注册表访问器会抛 NPE；
        // 暂存按键 NBT，待 setLevel 后再解析，避免方块实体加载失败（打字机键盘黑框 / 不加载）。
        this.typedEntry = tag.getString("typedEntry");
        final ListTag keysTag = tag.getList("Keys", 10);
        if (this.getLevel() != null) {
            this.entryMap = LinkedTypewriterEntries.readKeys(this.getLevel().registryAccess(), keysTag, this.getBlockPos());
        } else {
            this.pendingKeysTag = keysTag;
        }
        if (tag.contains("CurrentUser")) {
            this.currentUser = tag.getUUID("CurrentUser");
        } else {
            this.currentUser = null;
        }
    }

    @Override
    public void setLevel(final Level level) {
        super.setLevel(level);
        if (level != null && this.pendingKeysTag != null) {
            this.entryMap = LinkedTypewriterEntries.readKeys(level.registryAccess(), this.pendingKeysTag, this.getBlockPos());
            this.pendingKeysTag = null;
        }
    }

    public String getTypedEntry() {
        return this.typedEntry;
    }

    @Override
    public void invalidate() {
        this.pressedKeys.clear();
        this.entryMap.deactivateAll();
        this.entryMap.updateNetworks(this.getLevel());

        super.invalidate();
    }

    @Override
    public void destroy() {
        this.pressedKeys.clear();
        this.entryMap.deactivateAll();
        this.entryMap.updateNetworks(this.getLevel());

        super.destroy();
    }

    @Override
    public  AbstractContainerMenu createMenu(final int id, final Inventory inventory, final Player player) {
        return LinkedTypewriterMenuCommon.create(id, inventory, this);
    }

    @Override
    public Component getDisplayName() {
        return SimBlocks.LINKED_TYPEWRITER.get().getName();
    }

    @Override
    public String getClipboardKey() {
        return "TypewriterKeys";
    }

        public boolean writeToClipboard(final CompoundTag tag, final Direction side) {
        tag.put("Keys", this.entryMap.saveKeys(this.getLevel().registryAccess()));
        return true;
    }

        public boolean readFromClipboard(final CompoundTag tag, final Player player, final Direction side, final boolean simulate) {
        if (simulate) {
            return true;
        }
        this.entryMap = LinkedTypewriterEntries.readKeys(this.getLevel().registryAccess(), tag.getList("Keys", 10), this.getBlockPos());
        return true;
    }

}
