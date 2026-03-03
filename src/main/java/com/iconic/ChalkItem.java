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
        BlockState state = world.getBlockState(pos);

        double offset = 0.501;
        Vec3d spawnPos = Vec3d.ofCenter(pos).add(
                side.getOffsetX() * offset,
                side.getOffsetY() * offset,
                side.getOffsetZ() * offset
        );

        Box searchBox = Box.of(spawnPos, 0.1, 0.1, 0.1);
        List<DisplayEntity.ItemDisplayEntity> existingDisplays = world.getEntitiesByClass(
                DisplayEntity.ItemDisplayEntity.class,
                searchBox,
                entity -> entity.getCommandTags().contains("iconic_chalk")
        );

        // --- ЛОГИКА СТИРАНИЯ ---
        if (player.isSneaking()) {
            if (!existingDisplays.isEmpty()) {
                if (!world.isClient()) {
                    existingDisplays.get(0).discard();

                    // Новый, идеальный звук стирания (археологическая кисть)
                    world.playSound(null, pos, SoundEvents.ITEM_BRUSH_BRUSHING_GENERIC, SoundCategory.BLOCKS, 1.0F, 1.0F);
                }
                return ActionResult.SUCCESS;
            }
        }

        ItemStack stackInOffHand = player.getOffHandStack();

        if (stackInOffHand.isEmpty()) {
            if (!world.isClient()) {
                player.sendMessage(Text.literal("Возьмите предмет в левую руку!"), true);
            }
            return ActionResult.FAIL;
        }

        boolean isSolidSquare = state.isSideSolidFullSquare(world, pos, side);
        boolean isChest = state.getBlock() instanceof AbstractChestBlock;

        if (!isSolidSquare && !isChest) {
            return ActionResult.FAIL;
        }

        if (!existingDisplays.isEmpty()) {
            return ActionResult.FAIL;
        }

        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }

        Direction playerFacing = player.getHorizontalFacing();

        DisplayEntity.ItemDisplayEntity itemDisplay = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
        itemDisplay.setItemStack(stackInOffHand.copy());
        itemDisplay.setItemDisplayContext(ItemDisplayContext.GUI);

        itemDisplay.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, 0, 0);

        itemDisplay.setTransformation(new AffineTransformation(
                null,
                getRotationForSide(side, playerFacing),
                new Vector3f(0.7f, 0.7f, 0.001f),
                null
        ));

        itemDisplay.addCommandTag("iconic_chalk");
        itemDisplay.addCommandTag("chalk_dir_" + side.name());

        world.spawnEntity(itemDisplay);

        // --- ЭФФЕКТЫ РИСОВАНИЯ ---
        world.playSound(null, pos, SoundEvents.BLOCK_CALCITE_PLACE, SoundCategory.BLOCKS, 1.0F, 1.2F);

        if (world instanceof ServerWorld serverWorld) {
            // Увеличили количество до 25 и добавили скорость 0.05, чтобы пыль красиво разлеталась
            serverWorld.spawnParticles(ParticleTypes.WHITE_ASH, spawnPos.x, spawnPos.y, spawnPos.z,
                    25, 0.15, 0.15, 0.15, 0.05);
        }

        context.getStack().damage(1, player, net.minecraft.entity.EquipmentSlot.MAINHAND);

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
                    case NORTH -> rotation.rotateZ(0);
                    case EAST -> rotation.rotateZ((float) Math.toRadians(-90));
                    case SOUTH -> rotation.rotateZ((float) Math.toRadians(180));
                    case WEST -> rotation.rotateZ((float) Math.toRadians(90));
                }
            }
        }
        return rotation;
    }
}