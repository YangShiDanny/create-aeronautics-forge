package dev.simulated_team.simulated.mixin_interface.assembly_preventer;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public interface PrimaryAssemblerExtension {

    
    BlockPos simulated$getPrimaryAssembler();

    void simulated$setPrimaryAssembler( BlockPos pos);
}
