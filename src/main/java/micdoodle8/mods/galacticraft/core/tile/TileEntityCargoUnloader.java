/*
 * Copyright (c) 2023 Team Galacticraft
 *
 * Licensed under the MIT license.
 * See LICENSE file in the project root for details.
 */

package micdoodle8.mods.galacticraft.core.tile;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import net.minecraftforge.fml.relauncher.Side;

import micdoodle8.mods.miccore.Annotations.NetworkedField;

import micdoodle8.mods.galacticraft.annotations.ForRemoval;
import micdoodle8.mods.galacticraft.annotations.ReplaceWith;
import micdoodle8.mods.galacticraft.api.entity.ICargoEntity;
import micdoodle8.mods.galacticraft.api.entity.ICargoEntity.EnumCargoLoadingState;
import micdoodle8.mods.galacticraft.api.entity.ICargoEntity.RemovalResult;
import micdoodle8.mods.galacticraft.api.tile.ILandingPadAttachable;
import micdoodle8.mods.galacticraft.api.vector.BlockVec3;
import micdoodle8.mods.galacticraft.core.blocks.BlockCargoLoader;
import micdoodle8.mods.galacticraft.core.energy.item.ItemElectricBase;
import micdoodle8.mods.galacticraft.core.energy.tile.TileBaseElectricBlockWithInventory;
import micdoodle8.mods.galacticraft.core.util.RecipeUtil;

public class TileEntityCargoUnloader extends TileEntityAdvanced implements ILandingPadAttachable
{

    @NetworkedField(targetSide = Side.CLIENT) public boolean targetEmpty;
    @NetworkedField(targetSide = Side.CLIENT) public boolean targetNoInventory;
    @NetworkedField(targetSide = Side.CLIENT) public boolean noTarget;

    public ICargoEntity attachedFuelable;

    public TileEntityCargoUnloader()
    {
        super("container.cargounloader.name");
//        this.storage.setMaxExtract(45);
        this.inventory = NonNullList.withSize(15, ItemStack.EMPTY);
    }

    @Override
    public void update()
    {
        super.update();

        if (!this.world.isRemote)
        {
            if (this.ticks % 100 == 0)
            {
                this.checkForCargoEntity();
            }

            if (this.attachedFuelable != null)
            {
                this.noTarget = false;
                RemovalResult result = this.attachedFuelable.removeCargo(false, 8);

                if (!result.resultStack.isEmpty())
                {
                    this.targetEmpty = false;

                    EnumCargoLoadingState state = this.addCargo(result.resultStack, false);

                    this.targetEmpty = state == EnumCargoLoadingState.EMPTY;

                    if ((this.ticks % 5 == 0) && state == EnumCargoLoadingState.SUCCESS)
                    {
                        this.addCargo(this.attachedFuelable.removeCargo(true, 8).resultStack, true);
                    }
                } else
                {
                    this.targetNoInventory = result.resultState == EnumCargoLoadingState.NOINVENTORY;
                    this.noTarget = result.resultState == EnumCargoLoadingState.NOTARGET;
                    this.targetEmpty = true;
                }
            } else
            {
                this.noTarget = true;
            }
        }
    }

	@Override
	public double getPacketRange() {
		return 141;
	}

	@Override
	public int getPacketCooldown() {
		return 5;
	}

	@Override
	public boolean isNetworkedTile() {
		return false;
	}

	public void checkForCargoEntity()
    {
        boolean foundFuelable = false;

        BlockVec3 thisVec = new BlockVec3(this);
        for (final EnumFacing dir : EnumFacing.VALUES)
        {
            final TileEntity pad = thisVec.getTileEntityOnSide(this.world, dir);

            if (pad != null && pad instanceof TileEntityMulti)
            {
                final TileEntity mainTile = ((TileEntityMulti) pad).getMainBlockTile();

                if (mainTile instanceof ICargoEntity)
                {
                    this.attachedFuelable = (ICargoEntity) mainTile;
                    foundFuelable = true;
                    break;
                }
            } else if (pad != null && pad instanceof ICargoEntity)
            {
                this.attachedFuelable = (ICargoEntity) pad;
                foundFuelable = true;
                break;
            }
        }

        if (!foundFuelable)
        {
            this.attachedFuelable = null;
        }
    }

    @Override
    public boolean hasCustomName()
    {
        return false;
    }

    // ISidedInventory Implementation:

    @Override
    public int[] getSlotsForFace(EnumFacing side)
    {
        return new int[]
        {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14} ;
    }

    @Override
    public boolean canInsertItem(int slotID, ItemStack itemstack, EnumFacing side)
    {
        return false;
    }

    @Override
    public boolean canExtractItem(int slotID, ItemStack itemstack, EnumFacing side)
    {
            if (slotID == 0)
            {
                return ItemElectricBase.isElectricItemEmpty(itemstack);
            } else
            {
                return true;
            }
        }



    @Override
    public boolean isItemValidForSlot(int slotID, ItemStack itemstack)
    {
        if (slotID == 0)
        {
            return ItemElectricBase.isElectricItem(itemstack.getItem());
        } else
        {
            return true;
        }
    }



    public EnumCargoLoadingState addCargo(ItemStack stack, boolean doAdd)
    {
        int count = 1;

        for (count = 1; count < this.getInventory().size(); count++)
        {
            ItemStack stackAt = this.getInventory().get(count);

            if (RecipeUtil.stacksMatch(stack, stackAt) && stackAt.getCount() < stackAt.getMaxStackSize())
            {
                if (stackAt.getCount() + stack.getCount() <= stackAt.getMaxStackSize())
                {
                    if (doAdd)
                    {
                        stackAt.grow(stack.getCount());
                        this.markDirty();
                    }

                    return EnumCargoLoadingState.SUCCESS;
                } else
                {
                    // Part of the stack can fill this slot but there will be
                    // some left over
                    int origSize = stackAt.getCount();
                    int surplus = origSize + stack.getCount() - stackAt.getMaxStackSize();

                    if (doAdd)
                    {
                        stackAt.setCount(stackAt.getMaxStackSize());
                        this.markDirty();
                    }

                    stack.setCount(surplus);
                    if (this.addCargo(stack, doAdd) == EnumCargoLoadingState.SUCCESS)
                    {
                        return EnumCargoLoadingState.SUCCESS;
                    }

                    stackAt.setCount(origSize);
                    return EnumCargoLoadingState.FULL;
                }
            }
        }

        int size = this.getInventory().size();
        for (count = 1; count < size; count++)
        {
            ItemStack stackAt = this.getInventory().get(count);

            if (stackAt.isEmpty())
            {
                if (doAdd)
                {
                    this.getInventory().set(count, stack);
                    this.markDirty();
                }

                return EnumCargoLoadingState.SUCCESS;
            }
        }

        return EnumCargoLoadingState.FULL;
    }

    @Override
    public boolean canAttachToLandingPad(IBlockAccess world, BlockPos pos)
    {
        return true;
    }


    public EnumFacing byIndex()
    {
        IBlockState state = this.world.getBlockState(getPos());
        if (state.getBlock() instanceof BlockCargoLoader)
        {
            return (state.getValue(BlockCargoLoader.FACING));
        }
        return EnumFacing.NORTH;
    }




}
