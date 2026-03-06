package com.iconic;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.AbstractChestBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.DyeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * Main mod entry point for Iconic.
 * Handles item interaction, block destruction, and periodic world ticks.
 */
public class Iconic implements ModInitializer {
    public static final String MOD_ID = "iconic";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final BlockStateParticleEffect CHALK_CRUMBLE_EFFECT =
            new BlockStateParticleEffect(ParticleTypes.BLOCK_CRUMBLE, Blocks.CALCITE.getDefaultState());

    /**
     * Discards a chalk display entity with appropriate sounds and particles.
     * Also removes any associated background entities.
     */
    private static void discardChalkWithEffects(DisplayEntity.ItemDisplayEntity entity, World world) {
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        
        world.playSound(null, entity.getBlockPos(), SoundEvents.BLOCK_CALCITE_BREAK, SoundCategory.BLOCKS, 0.5F, 1.5F);
        
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(CHALK_CRUMBLE_EFFECT, x, y, z, 12, 0.12, 0.12, 0.12, 0.04);
        }
        
        entity.discard();
        
        // Find and discard associated backgrounds
        Box searchBox = Box.of(new Vec3d(x, y, z), 0.1, 0.1, 0.1);
        List<DisplayEntity.ItemDisplayEntity> backgrounds = world.getEntitiesByClass(
                DisplayEntity.ItemDisplayEntity.class,
                searchBox,
                e -> e.getCommandTags().contains(ModConstants.CHALK_BG_TAG)
        );
        
        for (DisplayEntity.ItemDisplayEntity bg : backgrounds) {
            bg.discard();
        }
    }

    @Override
    public void onInitialize() {
        ModItems.registerModItems();

        // Handle interaction priority for ChalkItem, Dyes, and Glow Ink
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player.isSpectator()) return ActionResult.PASS;

            // 1. ChalkItem Priority Handling
            // To fix hand priority (e.g., chalk in off-hand vs block in main hand),
            // we check both hands during the main hand interaction pass.
            if (hand == Hand.MAIN_HAND) {
                ItemStack mainStack = player.getMainHandStack();
                ItemStack offStack = player.getOffHandStack();
                
                // If main hand has chalk, let it handle itself normally in this pass
                if (mainStack.getItem() instanceof ChalkItem) {
                    ActionResult result = ChalkItem.useChalk(player, world, Hand.MAIN_HAND, hitResult.getBlockPos(), hitResult.getSide());
                    if (result != ActionResult.PASS) return result;
                }
                
                // If off-hand has chalk but main hand DOES NOT, handle off-hand chalk NOW
                // to prevent main hand block placement from taking priority.
                if (offStack.getItem() instanceof ChalkItem && !(mainStack.getItem() instanceof ChalkItem)) {
                    ActionResult result = ChalkItem.useChalk(player, world, Hand.OFF_HAND, hitResult.getBlockPos(), hitResult.getSide());
                    if (result == ActionResult.SUCCESS) return ActionResult.CONSUME;
                    if (result != ActionResult.PASS) return result;
                }
            } else {
                // For the off-hand pass, only handle if we haven't already (usually redundant but safe)
                ItemStack mainStack = player.getMainHandStack();
                ItemStack offStack = player.getOffHandStack();
                if (offStack.getItem() instanceof ChalkItem && !(mainStack.getItem() instanceof ChalkItem)) {
                    return ChalkItem.useChalk(player, world, Hand.OFF_HAND, hitResult.getBlockPos(), hitResult.getSide());
                }
            }

            // 2. Handle Dyes and Glow Ink (standard logic)
            ItemStack handStack = player.getStackInHand(hand);
            boolean isGlowInk = handStack.isOf(Items.GLOW_INK_SAC);
            DyeColor dyeColor = null;
            if (handStack.getItem() instanceof DyeItem dye) {
                dyeColor = dye.getColor();
            }

            if (!isGlowInk && dyeColor == null) return ActionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);
            boolean hasInterface = state.getBlock() instanceof net.minecraft.block.InventoryProvider || world.getBlockEntity(pos) != null;

            if (hasInterface && !player.isSneaking()) {
                return ActionResult.PASS;
            }

            Direction side = hitResult.getSide();
            Vec3d searchPos = Vec3d.ofCenter(pos).add(
                    side.getOffsetX() * ModConstants.CHALK_OFFSET,
                    side.getOffsetY() * ModConstants.CHALK_OFFSET,
                    side.getOffsetZ() * ModConstants.CHALK_OFFSET
            );

            Box searchBox = Box.of(searchPos, 0.1, 0.1, 0.1);

            // Find all display entities at the target location
            List<DisplayEntity.ItemDisplayEntity> allDisplays = world.getEntitiesByClass(
                    DisplayEntity.ItemDisplayEntity.class, searchBox, e -> true
            );

            if (allDisplays.isEmpty()) return ActionResult.PASS;

            // Identify the main chalk display
            DisplayEntity.ItemDisplayEntity mainDisplay = null;
            for (var e : allDisplays) {
                if (world.isClient() || e.getCommandTags().contains(ModConstants.CHALK_TAG)) {
                    mainDisplay = e;
                    break;
                }
            }
            
            if (mainDisplay == null) return ActionResult.PASS;

            // --- Handle Dye Application ---
            if (dyeColor != null) {
                // Check if any display already has this color
                boolean alreadyColored = false;
                for (var e : allDisplays) {
                    if (FrameItem.hasSameColor(e.getItemStack(), dyeColor)) {
                        alreadyColored = true;
                        break;
                    }
                }

                if (alreadyColored) return ActionResult.FAIL;

                if (!world.isClient()) {
                    List<DisplayEntity.ItemDisplayEntity> bgs = allDisplays.stream()
                            .filter(e -> e.getCommandTags().contains(ModConstants.CHALK_BG_TAG)).toList();

                    ItemStack frameStack = FrameItem.createStack(dyeColor);

                    if (bgs.isEmpty()) {
                        Direction facing = Direction.NORTH;
                        for (String tag : mainDisplay.getCommandTags()) {
                            if (tag.startsWith("iconic:facing_")) {
                                try {
                                    facing = Direction.valueOf(tag.substring(14));
                                } catch (IllegalArgumentException ex) {
                                    Iconic.LOGGER.debug("Invalid iconic:facing tag: {}", tag);
                                }
                            }
                        }

                        DisplayEntity.ItemDisplayEntity bg = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
                        bg.setItemStack(frameStack);
                        bg.setItemDisplayContext(ItemDisplayContext.GUI);

                        Vec3d displayPos = new Vec3d(mainDisplay.getX(), mainDisplay.getY(), mainDisplay.getZ());
                        Vec3d bgPos = displayPos.subtract(Vec3d.of(side.getVector()).multiply(0.0005));
                        bg.refreshPositionAndAngles(bgPos.x, bgPos.y, bgPos.z, 0, 0);

                        bg.setTransformation(new AffineTransformation(
                                null,
                                ChalkHelper.getRotationForSide(side, facing),
                                new Vector3f(0.85f, 0.85f, 0.0005f),
                                null
                        ));

                        if (mainDisplay.getCommandTags().contains(ModConstants.GLOWING_TAG)) {
                            bg.setBrightness(new Brightness(15, 15));
                            bg.addCommandTag(ModConstants.GLOWING_TAG);
                        }

                        bg.addCommandTag(ModConstants.CHALK_BG_TAG);
                        world.spawnEntity(bg);
                    } else {
                        bgs.getFirst().setItemStack(frameStack);
                    }

                    if (!player.isCreative()) handStack.decrement(1);
                    world.playSound(null, hitResult.getBlockPos(), SoundEvents.ITEM_DYE_USE, SoundCategory.BLOCKS, 1.0F, 1.0F);
                }
                player.swingHand(hand);
                return ActionResult.SUCCESS;
            }

            // --- Handle Glow Ink Application ---
            if (isGlowInk) {
                boolean alreadyGlowing = false;
                for (var e : allDisplays) {
                    int packedBrightness = e.getBrightness();
                    if (packedBrightness != -1) { 
                        int blockLight = packedBrightness & 0xFFFF;
                        if (blockLight >= 15) {
                            alreadyGlowing = true;
                            break;
                        }
                    }
                }

                if (alreadyGlowing) return ActionResult.FAIL;

                if (!world.isClient()) {
                    mainDisplay.setBrightness(new Brightness(15, 15));
                    mainDisplay.addCommandTag(ModConstants.GLOWING_TAG);

                    List<DisplayEntity.ItemDisplayEntity> bgs = allDisplays.stream()
                            .filter(e -> e.getCommandTags().contains(ModConstants.CHALK_BG_TAG)).toList();

                    for (DisplayEntity.ItemDisplayEntity bg : bgs) {
                        bg.setBrightness(new Brightness(15, 15));
                        bg.addCommandTag(ModConstants.GLOWING_TAG);
                    }

                    if (!player.isCreative()) handStack.decrement(1);
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

        // Remove chalk displays when the supporting block is broken
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient()) return;

            Box searchBox = new Box(pos).expand(1.0);
            List<DisplayEntity.ItemDisplayEntity> chalks = world.getEntitiesByClass(
                    DisplayEntity.ItemDisplayEntity.class,
                    searchBox,
                    entity -> entity.getCommandTags().contains(ModConstants.CHALK_TAG)
            );

            for (DisplayEntity.ItemDisplayEntity entity : chalks) {
                Direction attachedDir = ChalkHelper.parseChalkDirectionTag(entity);
                if (attachedDir == null) continue;

                BlockPos attachedBlockPos = entity.getBlockPos().offset(attachedDir.getOpposite());
                BlockState attachedState = world.getBlockState(attachedBlockPos);
                
                if (!attachedState.isSideSolidFullSquare(world, attachedBlockPos, attachedDir)
                        && !(attachedState.getBlock() instanceof AbstractChestBlock)) {
                    discardChalkWithEffects(entity, world);
                }
            }
        });

        // Periodic check to ensure chalk displays are still supported
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % 20 == 0) {
                Set<Integer> checkedEntities = new HashSet<>();

                for (ServerPlayerEntity player : world.getPlayers()) {
                    Box searchBox = player.getBoundingBox().expand(32.0);
                    List<DisplayEntity.ItemDisplayEntity> chalks = world.getEntitiesByClass(
                            DisplayEntity.ItemDisplayEntity.class,
                            searchBox,
                            entity -> entity.getCommandTags().contains(ModConstants.CHALK_TAG)
                    );

                    for (DisplayEntity.ItemDisplayEntity entity : chalks) {
                        if (entity.isRemoved() || !checkedEntities.add(entity.getId())) continue;

                        Direction attachedDir = ChalkHelper.parseChalkDirectionTag(entity);
                        if (attachedDir == null) continue;

                        BlockPos attachedBlockPos = entity.getBlockPos().offset(attachedDir.getOpposite());
                        BlockState state = world.getBlockState(attachedBlockPos);
                        
                        if (!state.isSideSolidFullSquare(world, attachedBlockPos, attachedDir)
                                && !(state.getBlock() instanceof AbstractChestBlock)) {
                            discardChalkWithEffects(entity, world);
                        }
                    }
                }
            }
        });
    }
}
