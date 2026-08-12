package dev.ryanhcode.sable.mixinterface.respawn_point;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

public interface ServerPlayerRespawnExtension {
     UUID sable$getRespawnPoint();

    void sable$takeQueuedFreezeFrom(ServerPlayer oldPlayer);

     Pair<UUID, Vector3d> sable$getQueuedFreeze();
}
