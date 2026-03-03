package com.iconic;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.block.AbstractChestBlock;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld; // Импорт для частиц
import net.minecraft.particle.ParticleTypes; // Импорт типов частиц
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Iconic implements ModInitializer {
    public static final String MOD_ID = "iconic";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModItems.registerModItems();

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player.isSpectator()) return ActionResult.PASS;

            ItemStack stack = player.getStackInHand(hand);

            boolean isGlowInk = stack.isOf(Items.GLOW_INK_SAC);
            boolean isRegularInk = stack.isOf(Items.INK_SAC);

            if (!isGlowInk && !isRegularInk) return ActionResult.PASS;

            Direction side = hitResult.getSide();
            double offset = 0.501;
            Vec3d searchPos = Vec3d.ofCenter(hitResult.getBlockPos()).add(
                    side.getOffsetX() * offset,
                    side.getOffsetY() * offset,
                    side.getOffsetZ() * offset
            );

            Box searchBox = Box.of(searchPos, 0.1, 0.1, 0.1);
            List<DisplayEntity.ItemDisplayEntity> displays = world.getEntitiesByClass(
                    DisplayEntity.ItemDisplayEntity.class,
                    searchBox,
                    entity -> entity.getCommandTags().contains("iconic_chalk")
            );

            if (!displays.isEmpty()) {
                DisplayEntity.ItemDisplayEntity display = displays.get(0);

                // --- СВЕТЯЩИЙСЯ МЕШОК ---
                if (isGlowInk && !display.getCommandTags().contains("glowing")) {
                    if (!world.isClient()) {
                        display.setBrightness(new Brightness(15, 15));
                        display.addCommandTag("glowing");

                        if (!player.isCreative()) {
                            stack.decrement(1);
                        }

                        world.playSound(null, hitResult.getBlockPos(), SoundEvents.ITEM_GLOW_INK_SAC_USE, SoundCategory.BLOCKS, 1.0F, 1.0F);

                        // Светящиеся частицы
                        if (world instanceof ServerWorld serverWorld) {
                            serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, searchPos.x, searchPos.y, searchPos.z,
                                    6, 0.15, 0.15, 0.15, 0.04);
                        }
                    }
                    return ActionResult.SUCCESS;
                }

                // --- ОБЫЧНЫЙ ЧЕРНИЛЬНЫЙ МЕШОК ---
                if (isRegularInk && display.getCommandTags().contains("glowing")) {
                    if (!world.isClient()) {
                        display.setBrightness(null);
                        display.removeCommandTag("glowing");

                        if (!player.isCreative()) {
                            stack.decrement(1);
                        }

                        world.playSound(null, hitResult.getBlockPos(), SoundEvents.ITEM_INK_SAC_USE, SoundCategory.BLOCKS, 1.0F, 1.0F);

                        // Черные частицы чернил
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

        // ПРОВЕРКА НА ВИСЯЩИЕ В ВОЗДУХЕ РИСУНКИ
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % 20 == 0) {
                for (ServerPlayerEntity player : world.getPlayers()) {
                    Box searchBox = player.getBoundingBox().expand(64.0);
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
                                // Спавним частицы осыпающегося мела перед удалением
                                // world здесь уже является ServerWorld, поэтому instanceof не нужен
                                world.spawnParticles(ParticleTypes.WHITE_ASH,
                                        entity.getX(), entity.getY(), entity.getZ(),
                                        20, 0.15, 0.15, 0.15, 0.03);

                                // По желанию: можно добавить тихий звук осыпания
                                world.playSound(null, entity.getBlockPos(), SoundEvents.BLOCK_SAND_BREAK, SoundCategory.BLOCKS, 0.3F, 1.2F);

                                entity.discard();
                            }
                        }
                    }
                }
            }
        });

        LOGGER.info("Iconic Mod: Initialized with visual effects!");
    }
}