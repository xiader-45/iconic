package com.iconic;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.block.AbstractChestBlock;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public class ChalkItem extends Item {
    public ChalkItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        var world = context.getWorld();
        var player = context.getPlayer();
        if (player == null) return ActionResult.PASS;

        BlockPos pos = context.getBlockPos();
        Direction side = context.getSide();
        Vec3d spawnPos = Vec3d.ofCenter(pos).add(side.getOffsetX() * Iconic.CHALK_OFFSET, side.getOffsetY() * Iconic.CHALK_OFFSET, side.getOffsetZ() * Iconic.CHALK_OFFSET);
        Box searchBox = Box.of(spawnPos, 0.1, 0.1, 0.1);

        List<DisplayEntity.ItemDisplayEntity> existingDisplays = world.getEntitiesByClass(
                DisplayEntity.ItemDisplayEntity.class, searchBox,
                entity -> world.isClient() || entity.getCommandTags().contains("iconic_chalk")
        );

        if (player.isSneaking()) {
            if (!existingDisplays.isEmpty()) {
                if (!world.isClient()) {
                    DisplayEntity.ItemDisplayEntity chalk = existingDisplays.getFirst();
                    List<DisplayEntity.ItemDisplayEntity> bgs = world.getEntitiesByClass(
                            DisplayEntity.ItemDisplayEntity.class, searchBox,
                            e -> e.getCommandTags().contains("iconic_chalk_bg"));

                    if (chalk.getCommandTags().contains("glowing")) {
                        chalk.setBrightness(null);
                        chalk.removeCommandTag("glowing");
                        if (!bgs.isEmpty()) {
                            bgs.getFirst().setBrightness(null);
                            bgs.getFirst().removeCommandTag("glowing");
                        }
                    } else if (!bgs.isEmpty()) {
                        bgs.getFirst().discard();
                    } else {
                        chalk.discard();
                    }
                    world.playSound(null, pos, SoundEvents.ITEM_BRUSH_BRUSHING_GENERIC, SoundCategory.BLOCKS, 1.0F, 1.0F);
                }
                return ActionResult.SUCCESS;
            }
        }

        ItemStack offHand = player.getOffHandStack();
        if (offHand.isEmpty()) {
            if (!world.isClient()) player.sendMessage(Text.translatable("message.iconic.empty_offhand"), true);
            return ActionResult.FAIL;
        }

        BlockState state = world.getBlockState(pos);
        if (!state.isSideSolidFullSquare(world, pos, side) && !(state.getBlock() instanceof AbstractChestBlock)) return ActionResult.FAIL;
        if (!existingDisplays.isEmpty()) return ActionResult.FAIL;

        if (!world.isClient()) {
            DisplayEntity.ItemDisplayEntity itemDisplay = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
            itemDisplay.setItemStack(offHand.copy());
            itemDisplay.setItemDisplayContext(ItemDisplayContext.GUI);
            itemDisplay.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, 0, 0);
            itemDisplay.setTransformation(new AffineTransformation(null, getRotationForSide(side, player.getHorizontalFacing()), new Vector3f(0.7f, 0.7f, 0.001f), null));
            itemDisplay.addCommandTag("iconic_chalk");
            itemDisplay.addCommandTag("chalk_dir_" + side.name());
            itemDisplay.addCommandTag("chalk_facing_" + player.getHorizontalFacing().name());
            world.spawnEntity(itemDisplay);

            world.playSound(null, pos, SoundEvents.BLOCK_CALCITE_PLACE, SoundCategory.BLOCKS, 1.0F, 1.2F);
            if (world instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(ParticleTypes.WHITE_ASH, spawnPos.x, spawnPos.y, spawnPos.z,
                        25, 0.15, 0.15, 0.15, 0.05);
            }
            context.getStack().damage(1, player, net.minecraft.entity.EquipmentSlot.MAINHAND);
        }
        return ActionResult.SUCCESS;
    }

    private Quaternionf getRotationForSide(Direction side, Direction playerFacing) {
        Quaternionf rotation = new Quaternionf();
        switch (side) {
            case NORTH -> rotation.rotationY(0);
            case SOUTH -> rotation.rotationY((float) Math.toRadians(180));
            case EAST -> rotation.rotationY((float) Math.toRadians(270));
            case WEST -> rotation.rotationY((float) Math.toRadians(90));
            case UP -> {
                rotation.rotationX((float) Math.toRadians(90));
                switch (playerFacing) {
                    case NORTH -> rotation.rotateZ((float) Math.toRadians(180));
                    case EAST -> rotation.rotateZ((float) Math.toRadians(-90));
                    case SOUTH -> rotation.rotateZ(0);
                    case WEST -> rotation.rotateZ((float) Math.toRadians(90));
                }
            }
            case DOWN -> {
                rotation.rotationX((float) Math.toRadians(-90));
                switch (playerFacing) {
                    case NORTH -> rotation.rotateZ((float) Math.toRadians(180));
                    case EAST -> rotation.rotateZ((float) Math.toRadians(90));
                    case SOUTH -> rotation.rotateZ(0);
                    case WEST -> rotation.rotateZ((float) Math.toRadians(-90));
                }
            }
        }
        return rotation;
    }
}