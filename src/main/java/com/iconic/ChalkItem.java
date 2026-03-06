package com.iconic;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.List;

/**
 * An item used to draw item icons on block surfaces.
 * Uses Display Entities for efficient rendering.
 */
public class ChalkItem extends Item {
    public ChalkItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        return useChalk(context.getPlayer(), context.getWorld(), context.getHand(), context.getBlockPos(), context.getSide());
    }

    /**
     * Central logic for chalk interaction. 
     * Handles both placing and removing displays.
     */
    public static ActionResult useChalk(PlayerEntity player, World world, Hand hand, BlockPos pos, Direction side) {
        if (player == null) return ActionResult.PASS;

        ItemStack chalkStack = player.getStackInHand(hand);
        if (!(chalkStack.getItem() instanceof ChalkItem)) return ActionResult.PASS;

        // Find existing chalk display at this face
        ItemDisplayEntity existingChalk = ChalkHelper.findChalkAt(world, pos, side);
        BlockState state = world.getBlockState(pos);
        boolean hasInterface = state.getBlock() instanceof InventoryProvider || world.getBlockEntity(pos) != null;

        if (player.isSneaking() && existingChalk != null) {
            if (!world.isClient()) {
                handleRemoval(world, pos, existingChalk);
                chalkStack.damage(1, player, hand == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
            player.swingHand(hand);
            return ActionResult.SUCCESS;
        }

        if (hasInterface && !player.isSneaking()) {
            return ActionResult.PASS;
        }

        Hand otherHand = hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
        ItemStack iconStack = player.getStackInHand(otherHand);

        if (iconStack.isEmpty()) {
            if (world.isClient()) {
                player.sendMessage(Text.translatable("message.iconic.empty_offhand"), true);
            }
            return ActionResult.FAIL;
        }

        // Check if the surface is valid for drawing
        if (!state.isSideSolidFullSquare(world, pos, side) && !isExceptionBlock(state)) {
            return ActionResult.FAIL;
        }

        if (existingChalk != null) {
            return ActionResult.CONSUME;
        }

        if (!world.isClient()) {
            placeChalk(world, player, pos, side, iconStack);
            chalkStack.damage(1, player, hand == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
        player.swingHand(hand);

        return ActionResult.SUCCESS;
    }

    private static boolean isExceptionBlock(BlockState state) {
        // Add specific blocks that aren't "full solid" but should be drawable
        return state.getBlock() instanceof AbstractChestBlock || 
               state.getBlock() instanceof BarrelBlock ||
               state.getBlock() instanceof EnderChestBlock ||
               state.getBlock() instanceof ShulkerBoxBlock;
    }

    private static void handleRemoval(World world, BlockPos pos, ItemDisplayEntity chalk) {
        // Use a box that covers the chalk entity to find its background
        Box box = chalk.getBoundingBox().expand(0.05);
        List<ItemDisplayEntity> backgrounds = world.getEntitiesByClass(
                ItemDisplayEntity.class, box,
                e -> e.getCommandTags().contains(ModConstants.CHALK_BG_TAG));

        if (chalk.getCommandTags().contains(ModConstants.GLOWING_TAG)) {
            // Layer 1: Remove glow
            chalk.setBrightness(null);
            chalk.removeCommandTag(ModConstants.GLOWING_TAG);
            for (ItemDisplayEntity bg : backgrounds) {
                bg.setBrightness(null);
                bg.removeCommandTag(ModConstants.GLOWING_TAG);
            }
        } else if (!backgrounds.isEmpty()) {
            // Layer 2: Remove background/color
            backgrounds.getFirst().discard();
        } else {
            // Layer 3: Remove the drawing itself
            chalk.discard();
        }

        world.playSound(null, pos, SoundEvents.ITEM_BRUSH_BRUSHING_GENERIC, SoundCategory.BLOCKS, 1.0F, 1.0F);
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.WHITE_ASH, chalk.getX(), chalk.getY(), chalk.getZ(),
                    6, 0.08, 0.08, 0.08, 0.02);
        }
    }

    private static void placeChalk(World world, PlayerEntity player, BlockPos pos, Direction side, ItemStack iconStack) {
        Vec3d spawnPos = Vec3d.ofCenter(pos).add(
            side.getOffsetX() * ModConstants.CHALK_OFFSET, 
            side.getOffsetY() * ModConstants.CHALK_OFFSET, 
            side.getOffsetZ() * ModConstants.CHALK_OFFSET
        );

        ItemDisplayEntity itemDisplay = new ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
        itemDisplay.setItemStack(iconStack.copy());
        itemDisplay.setItemDisplayContext(ItemDisplayContext.GUI);
        itemDisplay.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, 0, 0);
        
        itemDisplay.setTransformation(new AffineTransformation(
            null, 
            ChalkHelper.getRotationForSide(side, player.getHorizontalFacing()), 
            ModConstants.CHALK_SCALE, 
            null
        ));
        
        itemDisplay.addCommandTag(ModConstants.CHALK_TAG);
        itemDisplay.addCommandTag(ChalkHelper.getDirectionTag(side));
        itemDisplay.addCommandTag("iconic:facing_" + player.getHorizontalFacing().name());
        
        world.spawnEntity(itemDisplay);

        world.playSound(null, pos, SoundEvents.BLOCK_CALCITE_PLACE, SoundCategory.BLOCKS, 1.0F, 1.2F);
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.WHITE_ASH, spawnPos.x, spawnPos.y, spawnPos.z,
                    25, 0.15, 0.15, 0.15, 0.05);
        }
    }
}
