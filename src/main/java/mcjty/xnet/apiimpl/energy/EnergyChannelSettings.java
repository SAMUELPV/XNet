package mcjty.xnet.apiimpl.energy;

import mcjty.lib.varia.EnergyTools;
import mcjty.xnet.api.channels.IChannelSettings;
import mcjty.xnet.api.channels.IConnectorSettings;
import mcjty.xnet.api.channels.IControllerContext;
import mcjty.xnet.api.gui.IEditorGui;
import mcjty.xnet.api.gui.IndicatorIcon;
import mcjty.xnet.api.keys.SidedConsumer;
import mcjty.xnet.blocks.cables.ConnectorTileEntity;
import mcjty.xnet.blocks.controller.GuiController;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnergyChannelSettings implements IChannelSettings {

    public static final String TAG_MODE = "mode";

    enum ChannelMode {
        PRIORITY,
        DISTRIBUTE
    }

    private ChannelMode channelMode = ChannelMode.DISTRIBUTE;

    // Cache data
    private Map<SidedConsumer, EnergyConnectorSettings> energyExtractors = null;
    private List<Pair<SidedConsumer, EnergyConnectorSettings>> energyConsumers = null;

    public ChannelMode getChannelMode() {
        return channelMode;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        channelMode = ChannelMode.values()[tag.getByte("mode")];
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setByte("mode", (byte) channelMode.ordinal());
    }

    @Override
    public void tick(int channel, IControllerContext context) {
        updateCache(channel, context);
        // @todo optimize
        for (Map.Entry<SidedConsumer, EnergyConnectorSettings> entry : energyExtractors.entrySet()) {
            BlockPos consumerPosition = context.findConsumerPosition(entry.getKey().getConsumerId());
            if (consumerPosition != null) {
                EnumFacing side = entry.getKey().getSide();
                BlockPos pos = consumerPosition.offset(side);
                TileEntity te = context.getControllerWorld().getTileEntity(pos);
                // @todo report error somewhere?
                if (EnergyTools.isEnergyTE(te)) {
                    EnergyConnectorSettings settings = entry.getValue();
                    ConnectorTileEntity connectorTE = (ConnectorTileEntity) context.getControllerWorld().getTileEntity(consumerPosition);
                    Integer rate = settings.getRate();
                    if (rate == null) {
                        rate = 1000000000;
                    }
                    connectorTE.setEnergyInputFrom(side, rate);
                    int tosend = Math.max(rate, connectorTE.getEnergy());
                    if (tosend > 0) {
                        int actuallysent = insertEnergy(context, tosend);
                        connectorTE.setEnergy(connectorTE.getEnergy() - actuallysent);
                    }
                }
            }
        }
    }

    private int insertEnergy(@Nonnull IControllerContext context, int energy) {
        int total = energy;
        for (Pair<SidedConsumer, EnergyConnectorSettings> entry : energyConsumers) {
            EnergyConnectorSettings settings = entry.getValue();
            BlockPos consumerPosition = context.findConsumerPosition(entry.getKey().getConsumerId());
            if (consumerPosition != null) {
                EnumFacing side = entry.getKey().getSide();
                BlockPos pos = consumerPosition.offset(side);
                TileEntity te = context.getControllerWorld().getTileEntity(pos);
                // @todo report error somewhere?
                if (EnergyTools.isEnergyTE(te)) {
                    Integer rate = settings.getRate();
                    if (rate == null) {
                        rate = 1000000000;
                    }
                    int totransfer = Math.max(rate, energy);
                    energy -= EnergyTools.receiveEnergy(te, side.getOpposite(), totransfer);
                    if (energy <= 0) {
                        return total;
                    }
                }
            }
        }
        return total - energy;
    }

    @Override
    public void cleanCache() {
        energyExtractors = null;
        energyConsumers = null;
    }

    private void updateCache(int channel, IControllerContext context) {
        if (energyExtractors == null) {
            energyExtractors = new HashMap<>();
            energyConsumers = new ArrayList<>();
            Map<SidedConsumer, IConnectorSettings> connectors = context.getConnectors(channel);
            for (Map.Entry<SidedConsumer, IConnectorSettings> entry : connectors.entrySet()) {
                EnergyConnectorSettings con = (EnergyConnectorSettings) entry.getValue();
                if (con.getEnergyMode() == EnergyConnectorSettings.EnergyMode.EXT) {
                    energyExtractors.put(entry.getKey(), con);
                } else {
                    energyConsumers.add(Pair.of(entry.getKey(), con));
                }
            }
            energyConsumers.sort((o1, o2) -> o2.getRight().getPriority().compareTo(o1.getRight().getPriority()));
        }
    }

    @Override
    public boolean isEnabled(String tag) {
        return true;
    }

    @Nullable
    @Override
    public IndicatorIcon getIndicatorIcon() {
        return new IndicatorIcon(GuiController.iconGuiElements, 11, 80, 11, 10);
    }

    @Nullable
    @Override
    public String getIndicator() {
        return null;
    }

    @Override
    public void createGui(IEditorGui gui) {
        gui.nl().choices(TAG_MODE, "Energy distribution mode", channelMode, ChannelMode.values());
    }

    @Override
    public void update(Map<String, Object> data) {
        channelMode = ChannelMode.valueOf(((String)data.get(TAG_MODE)).toUpperCase());
    }
}