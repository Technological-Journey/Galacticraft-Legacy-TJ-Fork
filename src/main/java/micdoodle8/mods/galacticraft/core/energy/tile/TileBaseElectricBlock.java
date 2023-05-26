/*
 * Copyright (c) 2023 Team Galacticraft
 *
 * Licensed under the MIT license.
 * See LICENSE file in the project root for details.
 */

package micdoodle8.mods.galacticraft.core.energy.tile;

import com.google.common.collect.Lists;
import ic2.api.tile.IWrenchable;
import java.util.EnumSet;
import java.util.List;
import micdoodle8.mods.galacticraft.api.tile.IDisableableMachine;
import micdoodle8.mods.galacticraft.api.transmission.NetworkType;
import micdoodle8.mods.galacticraft.api.transmission.tile.IConnector;
import micdoodle8.mods.galacticraft.core.util.CompatibilityManager;
import micdoodle8.mods.galacticraft.core.util.EnumColor;
import micdoodle8.mods.galacticraft.core.util.GCCoreUtil;
import micdoodle8.mods.galacticraft.core.util.RedstoneUtil;
import micdoodle8.mods.miccore.Annotations.NetworkedField;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Optional.Interface;
import net.minecraftforge.fml.common.Optional.Method;
import net.minecraftforge.fml.relauncher.Side;

@Interface(iface = "ic2.api.tile.IWrenchable", modid = CompatibilityManager.modidIC2, striprefs = true)
public abstract class TileBaseElectricBlock extends TileBaseUniversalElectrical implements IDisableableMachine, IConnector, IWrenchable
{

    @NetworkedField(targetSide = Side.CLIENT)
    public boolean disabled             = false;
    @NetworkedField(targetSide = Side.CLIENT)
    public int     disableCooldown      = 0;
    @NetworkedField(targetSide = Side.CLIENT)
    public boolean hasEnoughEnergyToRun = false;
    public boolean noRedstoneControl = false;

    public TileBaseElectricBlock(String tileName)
    {
        super(tileName);
    }

    public boolean shouldPullEnergy()
    {
        return this.shouldUseEnergy() || this.getEnergyStoredGC(null) < this.getMaxEnergyStoredGC();
    }

    public abstract boolean shouldUseEnergy();

    public abstract EnumFacing getElectricInputDirection();

    public abstract ItemStack getBatteryInSlot();

    public int getScaledElecticalLevel(int i)
    {
        return (int) Math.floor(this.getEnergyStoredGC(null) * i / this.getMaxEnergyStoredGC(null));
    }

    @Override
    public void update()
    {
        if (!this.world.isRemote)
        {
            if (this.shouldPullEnergy() && this.getEnergyStoredGC(null) < this.getMaxEnergyStoredGC(null) && this.getBatteryInSlot() != null && this.getElectricInputDirection() != null)
            {
                this.discharge(this.getBatteryInSlot());
            }

            if (this.getEnergyStoredGC(null) > this.storage.getMaxExtract() && (this.noRedstoneControl || !RedstoneUtil.isBlockReceivingRedstone(this.world, this.getPos())))
            {
                this.hasEnoughEnergyToRun = true;
                if (this.shouldUseEnergy())
                {
                    this.storage.extractEnergyGC(this.storage.getMaxExtract(), false);
                }
            } else
            {
                this.hasEnoughEnergyToRun = false;
            }
        }

        super.update();

        if (!this.world.isRemote)
        {
            if (this.disableCooldown > 0)
            {
                this.disableCooldown--;
            }
        }
    }

    public void slowDischarge()
    {
        if (this.ticks % 10 == 0)
        {
            this.storage.extractEnergyGC(5F, false);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt)
    {
        super.writeToNBT(nbt);

        nbt.setBoolean("isDisabled", this.getDisabled(0));
        return nbt;
    }

    @Override
    public NBTTagCompound getUpdateTag()
    {
        return this.writeToNBT(new NBTTagCompound());
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt)
    {
        super.readFromNBT(nbt);

        this.setDisabled(0, nbt.getBoolean("isDisabled"));
    }

    @Override
    public void setDisabled(int index, boolean disabled)
    {
        if (this.disableCooldown == 0)
        {
            this.disabled = disabled;
            this.disableCooldown = 10;
        }
    }

    public abstract EnumFacing getFront();

    public EnumFacing byIndex() {
        return getFront();
    }

    @Override
    public boolean getDisabled(int index)
    {
        return this.disabled;
    }

    @Override
    @Method(modid = CompatibilityManager.modidIC2)
    public EnumFacing getFacing(World world, BlockPos pos)
    {
        return this.getFront();
    }

    @Override
    @Method(modid = CompatibilityManager.modidIC2)
    public boolean setFacing(World world, BlockPos pos, EnumFacing newDirection, EntityPlayer player)
    {
        return false;
    }

    @Override
    @Method(modid = CompatibilityManager.modidIC2)
    public boolean wrenchCanRemove(World world, BlockPos pos, EntityPlayer player)
    {
        return false;
    }

    @Override
    @Method(modid = CompatibilityManager.modidIC2)
    public List<ItemStack> getWrenchDrops(World world, BlockPos pos, IBlockState state, TileEntity te, EntityPlayer player, int fortune)
    {
        List<ItemStack> drops = Lists.newArrayList();
        drops.add(this.getBlockType().getPickBlock(state, null, this.world, this.getPos(), player));
        return drops;
    }

    @Override
    public EnumSet<EnumFacing> getElectricalInputDirections()
    {
        if (this.getElectricInputDirection() == null)
        {
            return EnumSet.noneOf(EnumFacing.class);
        }

        return EnumSet.of(this.getElectricInputDirection());
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer entityplayer)
    {
        return this.getWorld().getTileEntity(this.getPos()) == this && entityplayer.getDistanceSq(this.getPos().getX() + 0.5D, this.getPos().getY() + 0.5D, this.getPos().getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public boolean canConnect(EnumFacing direction, NetworkType type)
    {
        if (direction == null || type != NetworkType.POWER)
        {
            return false;
        }

        return direction == this.getElectricInputDirection();
    }

    public String getGUIstatus()
    {
        if (!this.noRedstoneControl && RedstoneUtil.isBlockReceivingRedstone(this.world, this.getPos()))
        {
            return EnumColor.DARK_RED + GCCoreUtil.translate("gui.status.off.name");
        }

        if (this.getEnergyStoredGC() == 0)
        {
            return EnumColor.DARK_RED + GCCoreUtil.translate("gui.status.missingpower.name");
        }

        if (this.getDisabled(0))
        {
            return EnumColor.ORANGE + GCCoreUtil.translate("gui.status.ready.name");
        }

        if (this.getEnergyStoredGC() < this.storage.getMaxExtract())
        {
            return EnumColor.ORANGE + GCCoreUtil.translate("gui.status.missingpower.name");
        }

        return EnumColor.DARK_GREEN + GCCoreUtil.translate("gui.status.active.name");
    }

    /**
     * @param missingInput = dynamically: null if all inputs are present, or a
     *        string if an input (e.g. oxygen, fuel) is missing
     * @param activeString = the specific 'Running' / 'Processing' etc string
     *        for this machine
     * @return
     */
    public String getGUIstatus(String missingInput, String activeString, boolean shorten)
    {
        if (!this.noRedstoneControl && RedstoneUtil.isBlockReceivingRedstone(this.world, this.getPos()))
        {
            return EnumColor.DARK_RED + GCCoreUtil.translate("gui.status.off.name");
        }

        if (this.getEnergyStoredGC() == 0)
        {
            return EnumColor.DARK_RED + GCCoreUtil.translate(shorten ? "gui.status.missingpower.short.name" : "gui.status.missingpower.name");
        }

        if (missingInput != null)
        {
            return missingInput;
        }

        if (this.getDisabled(0))
        {
            return EnumColor.ORANGE + GCCoreUtil.translate("gui.status.ready.name");
        }

        if (this.getEnergyStoredGC() < this.storage.getMaxExtract())
        {
            return EnumColor.ORANGE + GCCoreUtil.translate(shorten ? "gui.status.missingpower.short.name" : "gui.status.missingpower.name");
        }

        if (activeString != null)
        {
            return activeString;
        }

        return EnumColor.RED + GCCoreUtil.translate("gui.status.unknown.name");
    }
}
