package com.iconic;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.Item;
import net.minecraft.item.DyeItem;
import net.minecraft.util.DyeColor;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.block.AbstractChestBlock;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.particle.ParticleTypes;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Iconic implements ModInitializer {
    public static final String MOD_ID = "iconic";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final double CHALK_OFFSET = 0.501;

    private static Item getFrameItemForDye(DyeColor color) {
        return switch (color) {
            case WHITE -> ModItems.WHITE_FRAME;
            case ORANGE -> ModItems.ORANGE_FRAME;
            case MAGENTA -> ModItems.MAGENTA_FRAME;
            case LIGHT_BLUE -> ModItems.LIGHT_BLUE_FRAME;
            case YELLOW -> ModItems.YELLOW_FRAME;
            case LIME -> ModItems.LIME_FRAME;
            case PINK -> ModItems.PINK_FRAME;
            case GRAY -> ModItems.GRAY_FRAME;
            case LIGHT_GRAY -> ModItems.LIGHT_GRAY_FRAME;
            case CYAN -> ModItems.CYAN_FRAME;
            case PURPLE -> ModItems.PURPLE_FRAME;
            case BLUE -> ModItems.BLUE_FRAME;
            case BROWN -> ModItems.BROWN_FRAME;
            case GREEN -> ModItems.GREEN_FRAME;
            case RED -> ModItems.RED_FRAME;
            case BLACK -> null;
        };
    }

    private static Quaternionf getRotationForSide(Direction side, Direction playerFacing, float randomTilt) {
        Quaternionf rotation = new Quaternionf();
        switch (side) {
            case NORTH -> rotation.rotationY(0).rotateZ(randomTilt);
            case SOUTH -> rotation.rotationY((float) Math.toRadians(180)).rotateZ(randomTilt);
            case EAST -> rotation.rotationY((float) Math.toRadians(270)).rotateZ(randomTilt);
            case WEST -> rotation.rotationY((float) Math.toRadians(90)).rotateZ(randomTilt);
            case UP -> {
                rotation.rotationX((float) Math.toRadians(90));
                switch (playerFacing) {
                    case NORTH -> rotation.rotateZ((float) Math.toRadians(180));
                    case EAST -> rotation.rotateZ((float) Math.toRadians(-90));
                    case SOUTH -> rotation.rotateZ(0);
                    case WEST -> rotation.rotateZ((float) Math.toRadians(90));
                }
                rotation.rotateZ(randomTilt);
            }
            case DOWN -> {
                rotation.rotationX((float) Math.toRadians(-90));
                switch (playerFacing) {
                    case NORTH -> rotation.rotateZ(0);
                    case EAST -> rotation.rotateZ((float) Math.toRadians(-90));
                    case SOUTH -> rotation.rotateZ((float) Math.toRadians(180));
                    case WEST -> rotation.rotateZ((float) Math.toRadians(90));
                }
                rotation.rotateZ(randomTilt);
            }
        }
        return rotation;
    }

    @Override
    public void onInitialize() {
        ModItems.registerModItems();

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player.isSpectator()) return ActionResult.PASS;

            ItemStack stack = player.getStackInHand(hand);

            boolean isGlowInk = stack.isOf(Items.GLOW_INK_SAC);
            boolean isRegularInk = stack.isOf(Items.INK_SAC);
            boolean isDye = stack.getItem() instanceof DyeItem;

            if (!isGlowInk && !isRegularInk && !isDye) return ActionResult.PASS;

            Direction side = hitResult.getSide();
            Vec3d searchPos = Vec3d.ofCenter(hitResult.getBlockPos()).add(
                    side.getOffsetX() * CHALK_OFFSET,
                    side.getOffsetY() * CHALK_OFFSET,
                    side.getOffsetZ() * CHALK_OFFSET
            );

            Box searchBox = Box.of(searchPos, 0.1, 0.1, 0.1);

            // Фикс анимации (клиент всегда находит сущность)
            List<DisplayEntity.ItemDisplayEntity> displays = world.getEntitiesByClass(
                    DisplayEntity.ItemDisplayEntity.class,
                    searchBox,
                    entity -> world.isClient() || entity.getCommandTags().contains("iconic_chalk")
            );

            if (!displays.isEmpty()) {
                DisplayEntity.ItemDisplayEntity display = displays.getFirst();

                List<DisplayEntity.ItemDisplayEntity> bgs = world.getEntitiesByClass(
                        DisplayEntity.ItemDisplayEntity.class, searchBox,
                        e -> world.isClient() || e.getCommandTags().contains("iconic_chalk_bg"));

                if (isDye) {
                    if (!world.isClient()) {
                        DyeItem dyeItem = (DyeItem) stack.getItem();
                        Item frameItem = getFrameItemForDye(dyeItem.getColor());

                        if (frameItem == null) return ActionResult.PASS;

                        if (bgs.isEmpty()) {
                            float tilt = 0f;
                            Direction facing = Direction.NORTH;
                            for (String tag : display.getCommandTags()) {
                                if (tag.startsWith("chalk_tilt_")) {
                                    try { tilt = Float.parseFloat(tag.substring(11)); } catch (Exception e) {}
                                } else if (tag.startsWith("chalk_facing_")) {
                                    try { facing = Direction.valueOf(tag.substring(13)); } catch (Exception e) {}
                                }
                            }

                            DisplayEntity.ItemDisplayEntity bg = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
                            bg.setItemStack(new ItemStack(frameItem));
                            bg.setItemDisplayContext(ItemDisplayContext.GUI);

                            Vec3d displayPos = new Vec3d(display.getX(), display.getY(), display.getZ());
                            Vec3d bgPos = displayPos.subtract(Vec3d.of(side.getVector()).multiply(0.0005));
                            bg.refreshPositionAndAngles(bgPos.x, bgPos.y, bgPos.z, 0, 0);

                            bg.setTransformation(new AffineTransformation(
                                    null,
                                    getRotationForSide(side, facing, tilt),
                                    new Vector3f(0.85f, 0.85f, 0.0005f),
                                    null
                            ));

                            if (display.getCommandTags().contains("glowing")) {
                                bg.setBrightness(new Brightness(15, 15));
                                bg.addCommandTag("glowing");
                            }

                            bg.addCommandTag("iconic_chalk_bg");
                            world.spawnEntity(bg);
                        } else {
                            bgs.getFirst().setItemStack(new ItemStack(frameItem));
                        }

                        if (!player.isCreative()) stack.decrement(1);
                        world.playSound(null, hitResult.getBlockPos(), SoundEvents.ITEM_DYE_USE, SoundCategory.BLOCKS, 1.0F, 1.0F);
                    }
                    return ActionResult.SUCCESS;
                }

                if (isGlowInk && !display.getCommandTags().contains("glowing")) {
                    if (!world.isClient()) {
                        display.setBrightness(new Brightness(15, 15));
                        display.addCommandTag("glowing");

                        if (!bgs.isEmpty()) {
                            bgs.getFirst().setBrightness(new Brightness(15, 15));
                            bgs.getFirst().addCommandTag("glowing");
                        }

                        if (!player.isCreative()) stack.decrement(1);
                        world.playSound(null, hitResult.getBlockPos(), SoundEvents.ITEM_GLOW_INK_SAC_USE, SoundCategory.BLOCKS, 1.0F, 1.0F);

                        if (world instanceof ServerWorld serverWorld) {
                            serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, searchPos.x, searchPos.y, searchPos.z,
                                    6, 0.15, 0.15, 0.15, 0.04);
                        }
                    }
                    return ActionResult.SUCCESS;
                }

                // ДОБАВЛЕНО УСЛОВИЕ: Срабатывает только если рисунок светится ИЛИ есть рамка
                if (isRegularInk && (display.getCommandTags().contains("glowing") || !bgs.isEmpty())) {
                    if (!world.isClient()) {
                        // Убираем свечение
                        display.setBrightness(null);
                        display.removeCommandTag("glowing");

                        // Удаляем рамку
                        if (!bgs.isEmpty()) {
                            bgs.getFirst().discard();
                        }

                        if (!player.isCreative()) stack.decrement(1);
                        world.playSound(null, hitResult.getBlockPos(), SoundEvents.ITEM_INK_SAC_USE, SoundCategory.BLOCKS, 1.0F, 1.0F);

                        if (world instanceof ServerWorld serverWorld) {
                            serverWorld.spawnParticles(ParticleTypes.ASH, searchPos.x, searchPos.y, searchPos.z,
                                    15, 0.15, 0.15, 0.15, 0.02);
                        }
                    }
                    return ActionResult.SUCCESS;
                }
            }

            return ActionResult.PASS;
        });

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % 20 == 0) {
                for (ServerPlayerEntity player : world.getPlayers()) {
                    Box searchBox = player.getBoundingBox().expand(32.0);
                    List<DisplayEntity.ItemDisplayEntity> chalks = world.getEntitiesByClass(
                            DisplayEntity.ItemDisplayEntity.class,
                            searchBox,
                            entity -> entity.getCommandTags().contains("iconic_chalk")
                    );

                    for (DisplayEntity.ItemDisplayEntity entity : chalks) {
                        if (entity.isRemoved()) continue;

                        Direction attachedDir = null;

                        for (String tag : entity.getCommandTags()) {
                            if (tag.startsWith("chalk_dir_")) {
                                attachedDir = Direction.valueOf(tag.substring(10));
                                break;
                            }
                        }

                        if (attachedDir != null) {
                            BlockPos airPos = entity.getBlockPos();
                            BlockPos attachedBlockPos = airPos.offset(attachedDir.getOpposite());
                            BlockState state = world.getBlockState(attachedBlockPos);

                            boolean isSolidSquare = state.isSideSolidFullSquare(world, attachedBlockPos, attachedDir);
                            boolean isChest = state.getBlock() instanceof AbstractChestBlock;

                            if (!isSolidSquare && !isChest) {
                                entity.discard();

                                Vec3d entityPos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
                                List<DisplayEntity.ItemDisplayEntity> bgs = world.getEntitiesByClass(
                                        DisplayEntity.ItemDisplayEntity.class,
                                        Box.of(entityPos, 0.1, 0.1, 0.1),
                                        e -> e.getCommandTags().contains("iconic_chalk_bg")
                                );
                                if (!bgs.isEmpty()) {
                                    bgs.getFirst().discard();
                                }

                                world.spawnParticles(ParticleTypes.WHITE_ASH,
                                        entity.getX(), entity.getY(), entity.getZ(),
                                        20, 0.15, 0.15, 0.15, 0.03);

                                world.playSound(null, entity.getBlockPos(), SoundEvents.BLOCK_SAND_BREAK, SoundCategory.BLOCKS, 0.3F, 1.2F);
                            }
                        }
                    }
                }
            }
        });

        LOGGER.info("Iconic Mod: Custom Sprite Frames initialized!");
    }
}