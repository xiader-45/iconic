package com.iconic;

import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Shared utilities for chalk display logic.
 */
public final class ChalkHelper {

    private ChalkHelper() {}

    /**
     * Calculates the rotation for an item display based on the block face and player orientation.
     */
    public static Quaternionf getRotationForSide(Direction side, Direction playerFacing) {
        Quaternionf rotation = new Quaternionf();
        switch (side) {
            case NORTH -> rotation.rotationY(0);
            case SOUTH -> rotation.rotationY((float) Math.toRadians(180));
            case EAST -> rotation.rotationY((float) Math.toRadians(270));
            case WEST -> rotation.rotationY((float) Math.toRadians(90));
            case UP -> {
                rotation.rotationX((float) Math.toRadians(90));
                rotation.rotateZ(switch (playerFacing) {
                    case NORTH -> (float) Math.toRadians(180);
                    case EAST -> (float) Math.toRadians(-90);
                    case SOUTH -> 0;
                    case WEST -> (float) Math.toRadians(90);
                    default -> 0;
                });
            }
            case DOWN -> {
                rotation.rotationX((float) Math.toRadians(-90));
                rotation.rotateZ(switch (playerFacing) {
                    case NORTH -> (float) Math.toRadians(180);
                    case EAST -> (float) Math.toRadians(90);
                    case SOUTH -> 0;
                    case WEST -> (float) Math.toRadians(-90);
                    default -> 0;
                });
            }
            default -> {}
        }
        return rotation;
    }

    /**
     * Finds an existing chalk display entity at the specified block position and face.
     * @return the found ItemDisplayEntity or null if not found.
     */

    public static ItemDisplayEntity findChalkAt(World world, BlockPos pos, Direction side) {
        Box box = new Box(pos).expand(0.2);
        List<ItemDisplayEntity> displays = world.getEntitiesByClass(ItemDisplayEntity.class, box, e -> true);

        Vec3d expectedPos = Vec3d.ofCenter(pos).add(
                side.getOffsetX() * ModConstants.CHALK_OFFSET,
                side.getOffsetY() * ModConstants.CHALK_OFFSET,
                side.getOffsetZ() * ModConstants.CHALK_OFFSET
        );

        for (ItemDisplayEntity entity : displays) {
            if (world.isClient()) {
                Vec3d entityPos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());

                if (entityPos.squaredDistanceTo(expectedPos) < 0.01) {
                    return entity;
                }
            } else {
                if (!entity.getCommandTags().contains(ModConstants.CHALK_TAG)) continue;
                Direction dir = parseChalkDirectionTag(entity);
                if (dir == side) {
                    return entity;
                }
            }
        }
        return null;
    }

    /**
     * Parses the direction tag from an entity.
     * @return parsed Direction or null if tag is invalid or missing.
     */
    public static Direction parseChalkDirectionTag(DisplayEntity entity) {
        for (String tag : entity.getCommandTags()) {
            if (tag.startsWith("iconic:dir_")) {
                try {
                    return Direction.valueOf(tag.substring(11));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return null;
    }

    /**
     * Creates a direction tag string for the given direction.
     */
    public static String getDirectionTag(Direction direction) {
        return "iconic:dir_" + direction.name();
    }
}
