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
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

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
            case BLACK -> ModItems.BLACK_FRAME;
        };
    }

    private static Quaternionf getRotationForSide(Direction side, Direction playerFacing) {
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

    @Override
    public void onInitialize() {
        ModItems.registerModItems();

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player.isSpectator()) return ActionResult.PASS;

            ItemStack stack = player.getStackInHand(hand);
            boolean isGlowInk = stack.isOf(Items.GLOW_INK_SAC);

            DyeColor dyeColor = null;
            if (stack.getItem() instanceof DyeItem dye) {
                dyeColor = dye.getColor();
            }

            if (!isGlowInk && dyeColor == null) return ActionResult.PASS;

            Direction side = hitResult.getSide();
            Vec3d searchPos = Vec3d.ofCenter(hitResult.getBlockPos()).add(
                    side.getOffsetX() * CHALK_OFFSET,
                    side.getOffsetY() * CHALK_OFFSET,
                    side.getOffsetZ() * CHALK_OFFSET
            );

            Box searchBox = Box.of(searchPos, 0.1, 0.1, 0.1);

            // Получаем ВСЕ дисплеи в точке поиска
            List<DisplayEntity.ItemDisplayEntity> allDisplays = world.getEntitiesByClass(
                    DisplayEntity.ItemDisplayEntity.class, searchBox, e -> true
            );

            if (allDisplays.isEmpty()) return ActionResult.PASS;

            // Ищем главный дисплей (для сервера ищем по тегу, клиент берет любой первый)
            DisplayEntity.ItemDisplayEntity mainDisplay = null;
            for (var e : allDisplays) {
                if (world.isClient() || e.getCommandTags().contains("iconic_chalk")) {
                    mainDisplay = e;
                    break;
                }
            }
            if (mainDisplay == null) return ActionResult.PASS;

            // --- ОБРАБОТКА КРАСИТЕЛЯ ---
            if (dyeColor != null) {
                Item frameItem = getFrameItemForDye(dyeColor);
                if (frameItem == null) return ActionResult.PASS;

                // КЛИЕНТ-СЕРВЕРНАЯ ПРОВЕРКА: Если любой дисплей уже держит нужный цвет
                boolean alreadyColored = false;
                for (var e : allDisplays) {
                    if (e.getItemStack().isOf(frameItem)) {
                        alreadyColored = true;
                        break;
                    }
                }

                // Если уже покрашено, отменяем действие (без взмаха руки!)
                if (alreadyColored) {
                    return ActionResult.FAIL;
                }

                if (!world.isClient()) {
                    List<DisplayEntity.ItemDisplayEntity> bgs = allDisplays.stream()
                            .filter(e -> e.getCommandTags().contains("iconic_chalk_bg")).toList();

                    if (bgs.isEmpty()) {
                        Direction facing = Direction.NORTH;
                        for (String tag : mainDisplay.getCommandTags()) {
                            if (tag.startsWith("chalk_facing_")) {
                                try { facing = Direction.valueOf(tag.substring(13)); } catch (Exception e) {
                                    Iconic.LOGGER.error("Failed to parse chalk facing from tag: {}", tag);
                                }
                            }
                        }

                        DisplayEntity.ItemDisplayEntity bg = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
                        bg.setItemStack(new ItemStack(frameItem));
                        bg.setItemDisplayContext(ItemDisplayContext.GUI);

                        Vec3d displayPos = new Vec3d(mainDisplay.getX(), mainDisplay.getY(), mainDisplay.getZ());
                        Vec3d bgPos = displayPos.subtract(Vec3d.of(side.getVector()).multiply(0.0005));
                        bg.refreshPositionAndAngles(bgPos.x, bgPos.y, bgPos.z, 0, 0);

                        bg.setTransformation(new AffineTransformation(
                                null,
                                getRotationForSide(side, facing),
                                new Vector3f(0.85f, 0.85f, 0.0005f),
                                null
                        ));

                        if (mainDisplay.getCommandTags().contains("glowing")) {
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
                player.swingHand(hand);
                return ActionResult.SUCCESS;
            }

            // --- ОБРАБОТКА СВЕТЯЩЕГОСЯ МЕШКА ---
            if (isGlowInk) {

                // КЛИЕНТ-СЕРВЕРНАЯ ПРОВЕРКА: Если любой дисплей уже светится
                boolean alreadyGlowing = false;
                for (var e : allDisplays) {
                    int packedBrightness = e.getBrightness();

                    // В упакованном int значение 15 для блоков (block light)
                    // проверяется простым сравнением, если это кастомная яркость.
                    // Обычно, если мы установили Brightness(15, 15),
                    // getBrightness() вернет число, где блок-лайт равен 15.
                    if (packedBrightness != -1) { // -1 обычно означает "яркость по умолчанию"
                        // Извлекаем уровень света от блоков (младшие 16 бит)
                        int blockLight = packedBrightness & 0xFFFF;
                        if (blockLight >= 15) {
                            alreadyGlowing = true;
                            break;
                        }
                    }
                }

                // Если уже светится, отменяем действие (без взмаха руки!)
                if (alreadyGlowing) {
                    return ActionResult.FAIL;
                }

                if (!world.isClient()) {
                    mainDisplay.setBrightness(new Brightness(15, 15));
                    mainDisplay.addCommandTag("glowing");

                    List<DisplayEntity.ItemDisplayEntity> bgs = allDisplays.stream()
                            .filter(e -> e.getCommandTags().contains("iconic_chalk_bg")).toList();

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
                player.swingHand(hand);
                return ActionResult.SUCCESS;
            }

            return ActionResult.PASS;
        });
        // МГНОВЕННАЯ ПРОВЕРКА ПРИ ПОЛОМКЕ БЛОКА
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient()) return;

            // Создаем малюсенькую зону поиска только вокруг сломанного блока (расширяем на 1 блок во все стороны)
            Box searchBox = new Box(pos).expand(1.0);

            // Ищем все дисплеи мелков в этой микро-зоне
            List<DisplayEntity.ItemDisplayEntity> chalks = world.getEntitiesByClass(
                    DisplayEntity.ItemDisplayEntity.class,
                    searchBox,
                    entity -> entity.getCommandTags().contains("iconic_chalk")
            );

            for (DisplayEntity.ItemDisplayEntity entity : chalks) {
                Direction attachedDir = null;
                for (String tag : entity.getCommandTags()) {
                    if (tag.startsWith("chalk_dir_")) {
                        attachedDir = Direction.valueOf(tag.substring(10));
                        break;
                    }
                }

                if (attachedDir != null) {
                    BlockPos attachedBlockPos = entity.getBlockPos().offset(attachedDir.getOpposite());
                    BlockState attachedState = world.getBlockState(attachedBlockPos);

                    // Если блок, к которому прикреплен мелок, больше не твердый — уничтожаем
                    if (!attachedState.isSideSolidFullSquare(world, attachedBlockPos, attachedDir) && !(attachedState.getBlock() instanceof AbstractChestBlock)) {

                        world.playSound(null, entity.getBlockPos(), SoundEvents.BLOCK_CALCITE_BREAK, SoundCategory.BLOCKS, 0.5F, 1.5F);

                        entity.discard();

                        List<DisplayEntity.ItemDisplayEntity> bgs = world.getEntitiesByClass(
                                DisplayEntity.ItemDisplayEntity.class,
                                // Используем надежный способ получения позиции:
                                Box.of(new Vec3d(entity.getX(), entity.getY(), entity.getZ()), 0.1, 0.1, 0.1),
                                e -> e.getCommandTags().contains("iconic_chalk_bg")
                        );
                        if (!bgs.isEmpty()) bgs.getFirst().discard();
                    }
                }
            }
        });

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % 20 == 0) {
                // Создаем список ID сущностей, которые мы уже проверили в этом тике
                java.util.Set<Integer> checkedEntities = new java.util.HashSet<>();

                for (ServerPlayerEntity player : world.getPlayers()) {
                    Box searchBox = player.getBoundingBox().expand(32.0);
                    List<DisplayEntity.ItemDisplayEntity> chalks = world.getEntitiesByClass(
                            DisplayEntity.ItemDisplayEntity.class,
                            searchBox,
                            entity -> entity.getCommandTags().contains("iconic_chalk")
                    );

                    for (DisplayEntity.ItemDisplayEntity entity : chalks) {
                        // Если сущность мертва или мы её уже проверили (для другого игрока рядом) — пропускаем
                        if (entity.isRemoved() || !checkedEntities.add(entity.getId())) continue;

                        Direction attachedDir = null;
                        for (String tag : entity.getCommandTags()) {
                            if (tag.startsWith("chalk_dir_")) {
                                attachedDir = Direction.valueOf(tag.substring(10));
                                break;
                            }
                        }

                        if (attachedDir != null) {
                            BlockPos attachedBlockPos = entity.getBlockPos().offset(attachedDir.getOpposite());
                            BlockState state = world.getBlockState(attachedBlockPos);

                            if (!state.isSideSolidFullSquare(world, attachedBlockPos, attachedDir) && !(state.getBlock() instanceof AbstractChestBlock)) {

                                world.playSound(null, entity.getBlockPos(), SoundEvents.BLOCK_CALCITE_BREAK, SoundCategory.BLOCKS, 0.5F, 1.5F);

                                entity.discard();
                                List<DisplayEntity.ItemDisplayEntity> bgs = world.getEntitiesByClass(
                                        DisplayEntity.ItemDisplayEntity.class,
                                        Box.of(new Vec3d(entity.getX(), entity.getY(), entity.getZ()), 0.1, 0.1, 0.1),
                                        e -> e.getCommandTags().contains("iconic_chalk_bg")
                                );
                                if (!bgs.isEmpty()) bgs.getFirst().discard();
                            }
                        }
                    }
                }
            }
        });
    }
}