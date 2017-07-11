package org.cyclops.integratedtunnels.core;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fluids.capability.wrappers.BlockLiquidWrapper;
import net.minecraftforge.fluids.capability.wrappers.BlockWrapper;
import net.minecraftforge.fluids.capability.wrappers.FluidBlockWrapper;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.operator.IOperator;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueTypeListProxy;
import org.cyclops.integrateddynamics.api.part.PartTarget;
import org.cyclops.integrateddynamics.api.part.write.IPartStateWriter;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueHelpers;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueObjectTypeFluidStack;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeBoolean;
import org.cyclops.integrateddynamics.core.helper.PartHelpers;

import javax.annotation.Nullable;
import java.util.List;

/**
 * @author rubensworks
 */
public class TunnelFluidHelpers {

    public static final Predicate<FluidStack> MATCH_ALL = new Predicate<FluidStack>() {
        @Override
        public boolean apply(@Nullable FluidStack input) {
            return true;
        }
    };

    /**
     * Move all fluids matching the predicate from source to target.
     * @param source The source fluid handler.
     * @param target The target fluid handler.
     * @param maxAmount The maximum fluid amount to transfer.
     * @param doTransfer If transfer should actually happen, will simulate otherwise.
     * @param fluidStackMatcher The fluidstack match predicate.
     * @return The moved fluidstack or null.
     */
    @Nullable
    public static FluidStack moveFluids(IFluidHandler source, final IFluidHandler target, int maxAmount, boolean doTransfer, Predicate<FluidStack> fluidStackMatcher) {
        return moveFluids(source, new Function<FluidStack, IFluidHandler>() {
            @Nullable
            @Override
            public IFluidHandler apply(@Nullable FluidStack input) {
                return target;
            }
        }, maxAmount, doTransfer, fluidStackMatcher);
    }

    /**
     * Move all fluids matching the predicate from source to target.
     * @param source The source fluid handler.
     * @param targetGetter The target fluid handler getter.
     * @param maxAmount The maximum fluid amount to transfer.
     * @param doTransfer If transfer should actually happen, will simulate otherwise.
     * @param fluidStackMatcher The fluidstack match predicate.
     * @return The moved fluidstack or null.
     */
    @Nullable
    public static FluidStack moveFluids(IFluidHandler source, Function<FluidStack, IFluidHandler> targetGetter, int maxAmount, boolean doTransfer, Predicate<FluidStack> fluidStackMatcher) {
        List<FluidStack> checkFluids = Lists.newArrayList();
        for (IFluidTankProperties properties : source.getTankProperties()) {
            FluidStack contents = properties.getContents();
            if (contents != null) {
                FluidStack toMove = contents.copy();
                toMove.amount = maxAmount;
                if (fluidStackMatcher.apply(toMove)) {
                    checkFluids.add(toMove);
                }
            }
        }

        for (FluidStack checkFluid : checkFluids) {
            FluidStack drainable = source.drain(checkFluid, false);
            if (drainable != null && drainable.amount > 0) {
                IFluidHandler target = targetGetter.apply(drainable);
                if (target != null) {
                    int fillableAmount = target.fill(drainable, false);
                    if (fillableAmount > 0) {
                        FluidStack drained = source.drain(new FluidStack(drainable.getFluid(), fillableAmount), doTransfer);
                        if (drained != null) {
                            drained.amount = target.fill(drained, doTransfer);
                            return drained;
                        }
                    }
                }
            }
        }
        return null;
    }

    public static Predicate<FluidStack> matchFluidStack(final FluidStack fluidStack, final boolean checkAmount, final boolean checkNbt) {
        return new Predicate<FluidStack>() {
            @Override
            public boolean apply(@Nullable FluidStack input) {
                return areFluidStackEqual(input, fluidStack, true, checkAmount, checkNbt);
            }
        };
    }

    public static Predicate<FluidStack> matchFluidStacks(final IValueTypeListProxy<ValueObjectTypeFluidStack, ValueObjectTypeFluidStack.ValueFluidStack> fluidStacks,
                                                       final boolean checkAmount, final boolean checkNbt) {
        return new Predicate<FluidStack>() {
            @Override
            public boolean apply(@Nullable FluidStack input) {
                for (ValueObjectTypeFluidStack.ValueFluidStack fluidStack : fluidStacks) {
                    if (fluidStack.getRawValue().isPresent()
                            && areFluidStackEqual(input, fluidStack.getRawValue().get(), true, checkAmount, checkNbt)) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    public static Predicate<FluidStack> matchPredicate(final PartTarget partTarget, final IOperator predicate) {
        return new Predicate<FluidStack>() {
            @Override
            public boolean apply(@Nullable FluidStack input) {
                ValueObjectTypeFluidStack.ValueFluidStack valueFluidStack = ValueObjectTypeFluidStack.ValueFluidStack.of(input);
                try {
                    IValue result = ValueHelpers.evaluateOperator(predicate, valueFluidStack);
                    return ((ValueTypeBoolean.ValueBoolean) result).getRawValue();
                } catch (EvaluationException e) {
                    PartHelpers.PartStateHolder<?, ?> partData = PartHelpers.getPart(partTarget.getCenter());
                    if (partData != null) {
                        IPartStateWriter partState = (IPartStateWriter) partData.getState();
                        partState.addError(partState.getActiveAspect(), new L10NHelpers.UnlocalizedString(e.getMessage()));
                        partState.setDeactivated(true);
                    }
                    return false;
                }
            }
        };
    }

    public static boolean areFluidStackEqual(FluidStack stackA, FluidStack stackB,
                                             boolean checkFluid, boolean checkAmount, boolean checkNbt) {
        if (stackA == null && stackB == null) return true;
        if (stackA != null && stackB != null) {
            if (checkAmount && stackA.amount != stackB.amount) return false;
            if (checkFluid && stackA.getFluid() != stackB.getFluid()) return false;
            if (checkNbt && !FluidStack.areFluidStackTagsEqual(stackA, stackB)) return false;
            return true;
        }
        return false;
    }

    /**
     * Place fluids from the given source in the world.
     * @param source The source fluid handler.
     * @param world The target world.
     * @param pos The target position.
     * @param fluidStackMatcher The fluidstack match predicate.
     * @param blockUpdate If a block update should occur after placement.
     * @return The placed fluid.
     */
    public static FluidStack placeFluids(IFluidHandler source, final World world, final BlockPos pos,
                                         Predicate<FluidStack> fluidStackMatcher, boolean blockUpdate) {
        IBlockState destBlockState = world.getBlockState(pos);
        final Material destMaterial = destBlockState.getMaterial();
        final boolean isDestNonSolid = !destMaterial.isSolid();
        final boolean isDestReplaceable = destBlockState.getBlock().isReplaceable(world, pos);
        if (!world.isAirBlock(pos)
                && (!isDestNonSolid || !isDestReplaceable || destMaterial.isLiquid())) {
            return null;
        }

        FluidStack moved = TunnelFluidHelpers.moveFluids(source, new Function<FluidStack, IFluidHandler>() {
            @Nullable
            @Override
            public IFluidHandler apply(FluidStack input) {
                net.minecraftforge.fluids.Fluid fluid = input.getFluid();
                if (world.provider.doesWaterVaporize() && fluid.doesVaporize(input)) {
                    return null;
                }

                Block block = fluid.getBlock();
                IFluidHandler handler;
                if (block instanceof IFluidBlock) {
                    handler = new FluidBlockWrapper((IFluidBlock) block, world, pos);
                } else if (block instanceof BlockLiquid) {
                    handler = new BlockLiquidWrapper((BlockLiquid) block, world, pos);
                } else {
                    handler = new BlockWrapper(block, world, pos);
                }

                return handler;
            }
        }, Fluid.BUCKET_VOLUME, true, fluidStackMatcher);

        if (moved != null && blockUpdate) {
            world.neighborChanged(pos, Blocks.AIR, pos);
        }
        return moved;
    }

    /**
     * Place fluids from the given source in the world.
     * @param world The source world.
     * @param pos The source position.
     * @param side The source side.
     * @param target The source fluid handler.
     * @param fluidStackMatcher The fluidstack match predicate.
     * @return The picked-up fluid.
     */
    public static FluidStack pickUpFluids(World world, BlockPos pos, EnumFacing side, IFluidHandler target,
                                         Predicate<FluidStack> fluidStackMatcher) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof IFluidBlock || block instanceof BlockLiquid) {
            IFluidHandler targetFluidHandler = FluidUtil.getFluidHandler(world, pos, side);
            if (targetFluidHandler != null) {
                return TunnelFluidHelpers.moveFluids(targetFluidHandler, target, Fluid.BUCKET_VOLUME, true,
                        fluidStackMatcher);
            }
        }
        return null;
    }

}
