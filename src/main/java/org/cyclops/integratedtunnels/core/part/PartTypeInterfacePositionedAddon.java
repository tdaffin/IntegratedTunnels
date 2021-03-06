package org.cyclops.integratedtunnels.core.part;

import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.capabilities.Capability;

import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.cyclopscore.helper.TileHelpers;
import org.cyclops.integrateddynamics.api.network.INetwork;
import org.cyclops.integrateddynamics.api.network.IPartNetwork;
import org.cyclops.integrateddynamics.api.network.IPositionedAddonsNetwork;
import org.cyclops.integrateddynamics.api.part.IPartContainer;
import org.cyclops.integrateddynamics.api.part.IPartType;
import org.cyclops.integrateddynamics.api.part.PartPos;
import org.cyclops.integrateddynamics.api.part.PartTarget;
import org.cyclops.integrateddynamics.core.client.gui.container.GuiPartSettings;
import org.cyclops.integrateddynamics.core.part.PartStateBase;

import javax.annotation.Nullable;

/**
 * Interface for positioned network addons.
 * @author rubensworks
 */
public abstract class PartTypeInterfacePositionedAddon<N extends IPositionedAddonsNetwork, T, P extends IPartType<P, S>, S extends PartTypeInterfacePositionedAddon.State<P, N, T>> extends PartTypeTunnel<P, S> {
    public PartTypeInterfacePositionedAddon(String name) {
        super(name);
    }

    public static class GuiInterfaceSettings extends GuiPartSettings {
        public GuiInterfaceSettings(EntityPlayer player, PartTarget target, IPartContainer partContainer, IPartType partType) {
            super(player, target, partContainer, partType);
        }

        @Override
        protected String getChannelText() {
            return L10NHelpers.localize("gui.integratedtunnels.partsettings.channel");
        }
    }

    @Override
    public Class<? extends GuiScreen> getGui() {
        return GuiInterfaceSettings.class;
    }

    protected abstract Capability<N> getNetworkCapability();
    protected abstract Capability<T> getTargetCapability();
    protected boolean isTargetCapabilityValid(T capability) {
        return capability != null;
    }

    @Override
    public void afterNetworkReAlive(INetwork network, IPartNetwork partNetwork, PartTarget target, S state) {
        super.afterNetworkReAlive(network, partNetwork, target, state);
        addTargetToNetwork(network, target.getTarget(), state.getPriority(), state.getChannel(), state);
    }

    @Override
    public void onNetworkRemoval(INetwork network, IPartNetwork partNetwork, PartTarget target, S state) {
        super.onNetworkRemoval(network, partNetwork, target, state);
        removeTargetFromNetwork(network, target.getTarget(), state);
    }

    @Override
    public void onNetworkAddition(INetwork network, IPartNetwork partNetwork, PartTarget target, S state) {
        super.onNetworkAddition(network, partNetwork, target, state);
        addTargetToNetwork(network, target.getTarget(), state.getPriority(), state.getChannel(), state);
    }

    @Override
    public void onBlockNeighborChange(@Nullable INetwork network, @Nullable IPartNetwork partNetwork, PartTarget target, S state, IBlockAccess world, Block neighborBlock) {
        super.onBlockNeighborChange(network, partNetwork, target, state, world, neighborBlock);
        if (network != null) {
            updateTargetInNetwork(network, target.getTarget(), state.getPriority(), state.getChannel(), state);
        }
    }

    @Override
    public void setPriorityAndChannel(INetwork network, IPartNetwork partNetwork, PartTarget target, S state, int priority, int channel) {
        // We need to do this because the energy network is not automagically aware of the priority changes,
        // so we have to re-add it.
        removeTargetFromNetwork(network, target.getTarget(), state);
        super.setPriorityAndChannel(network, partNetwork, target, state, priority, channel);
        addTargetToNetwork(network, target.getTarget(), priority, channel, state);
    }

    protected T getTargetCapabilityInstance(PartPos pos) {
        return TileHelpers.getCapability(pos.getPos(), pos.getSide(), getTargetCapability());
    }

    protected void addTargetToNetwork(INetwork network, PartPos pos, int priority, int channel, S state) {
        if (network.hasCapability(getNetworkCapability())) {
            T capability = getTargetCapabilityInstance(pos);
            boolean validTargetCapability = isTargetCapabilityValid(capability);
            if (validTargetCapability) {
                N networkCapability = network.getCapability(getNetworkCapability());
                networkCapability.addPosition(pos, priority, channel);
            }
            state.setPositionedAddonsNetwork(network.getCapability(getNetworkCapability()));
            state.setPos(pos);
            state.setValidTargetCapability(validTargetCapability);
        }
    }

    protected void removeTargetFromNetwork(INetwork network, PartPos pos, S state) {
        if (network.hasCapability(getNetworkCapability())) {
            N networkCapability = network.getCapability(getNetworkCapability());
            networkCapability.removePosition(pos);
        }
        state.setPositionedAddonsNetwork(null);
        state.setPos(null);
        state.setValidTargetCapability(false);
    }

    protected void updateTargetInNetwork(INetwork network, PartPos pos, int priority, int channel, S state) {
        if (network.hasCapability(getNetworkCapability())) {
            T capability = getTargetCapabilityInstance(pos);
            boolean validTargetCapability = isTargetCapabilityValid(capability);
            boolean wasValidTargetCapability = state.isValidTargetCapability();
            // Only trigger a change if the capability presence has changed.
            if (validTargetCapability != wasValidTargetCapability) {
                removeTargetFromNetwork(network, pos, state);
                addTargetToNetwork(network, pos, priority, channel, state);
            }
        }
    }

    public static abstract class State<P extends IPartType, N extends IPositionedAddonsNetwork, T> extends PartStateBase<P> {

        private N positionedAddonsNetwork = null;
        private PartPos pos = null;
        private boolean validTargetCapability = false;

        protected abstract Capability<T> getTargetCapability();

        public N getPositionedAddonsNetwork() {
            return positionedAddonsNetwork;
        }

        public void setPositionedAddonsNetwork(N positionedAddonsNetwork) {
            this.positionedAddonsNetwork = positionedAddonsNetwork;
        }

        public boolean isValidTargetCapability() {
            return validTargetCapability;
        }

        public void setValidTargetCapability(boolean validTargetCapability) {
            this.validTargetCapability = validTargetCapability;
        }

        public PartPos getPos() {
            return pos;
        }

        public void setPos(PartPos pos) {
            this.pos = pos;
        }

        protected void disablePosition() {
            N positionedNetwork = getPositionedAddonsNetwork();
            PartPos pos = getPos();
            if (positionedNetwork != null) {
                positionedNetwork.disablePosition(pos);
            }
        }

        protected void enablePosition() {
            N positionedNetwork = getPositionedAddonsNetwork();
            PartPos pos = getPos();
            if (positionedNetwork != null) {
                positionedNetwork.enablePosition(pos);
            }
        }

        @Override
        public boolean hasCapability(Capability<?> capability) {
            return (getPositionedAddonsNetwork() != null && capability == getTargetCapability())
                    || super.hasCapability(capability);
        }

        @Override
        public <T2> T2 getCapability(Capability<T2> capability) {
            if (getPositionedAddonsNetwork() != null && capability == getTargetCapability()) {
                return (T2) this;
            }
            return super.getCapability(capability);
        }
    }
}
