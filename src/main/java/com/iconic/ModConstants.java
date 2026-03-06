package com.iconic;

import org.joml.Vector3f;

/**
 * Global constants for the Iconic mod.
 * Helps avoid "magic numbers" and strings across the codebase.
 */
public class ModConstants {
    public static final String CHALK_TAG = "iconic:chalk";
    public static final String CHALK_BG_TAG = "iconic:chalk_bg";
    public static final String GLOWING_TAG = "iconic:glowing";
    
    // Default scale for chalk displays
    public static final float CHALK_SCALE_X = 0.7f;
    public static final float CHALK_SCALE_Y = 0.7f;
    public static final float CHALK_SCALE_Z = 0.001f;
    
    public static final Vector3f CHALK_SCALE = new Vector3f(CHALK_SCALE_X, CHALK_SCALE_Y, CHALK_SCALE_Z);
    
    // Offset to prevent z-fighting with the block surface. 
    // Since we spawn from the center of the block, 0.5 is the face, and 0.001 is the tiny gap.
    public static final double CHALK_OFFSET = 0.501;
}
