package dev.simulated_team.simulated.multiloader.tanks.neoforge;

import dev.simulated_team.simulated.multiloader.tanks.CFluidType;
import dev.simulated_team.simulated.multiloader.tanks.SingleTank;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;

public class SingleTankWrapper extends FluidTank {
    private final SingleTank tank;

    public SingleTankWrapper(final SingleTank tank) {
        super((int) tank.capacity);
        this.tank = tank;
    }

    public static FluidStack fromCType(final CFluidType type, final int amount) {
        return new FluidStack(type.fluid, amount);
    }

    public static CFluidType toCType(final FluidStack stack) {
        // [1.20.1 移植] FluidStack 在 1.20.1 无数据组件表， fluids 不携带组件，传 null
        return new CFluidType(stack.getFluid(), null);
    }

    @Override
    public int fill(final FluidStack resource, final FluidAction action) {
        return (int) this.tank.insert(toCType(resource), resource.getAmount(), action.simulate());
    }

    @Override
    public  FluidStack drain(final int maxDrain, final FluidAction action) {
        return fromCType(this.tank.type, (int) this.tank.extract(this.tank.type, maxDrain, action.simulate()));
    }

    @Override
    public  FluidStack getFluid() {
        return fromCType(this.tank.type, (int) this.tank.amount);
    }
}
