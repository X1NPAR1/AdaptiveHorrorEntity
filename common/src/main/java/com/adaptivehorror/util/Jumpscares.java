package com.adaptivehorror.util;

import java.util.Random;

/**
 * Single source of truth for which jumpscare images actually exist as shipped textures. Picking from
 * this set (rather than a hardcoded {@code 1 + nextInt(8)} scattered across the codebase) means a new
 * image is wired up by editing one array, and a missing index can never be rolled - no purple/missing
 * texture flashes.
 *
 * <p>Index {@code 9} is intentionally absent (no {@code jumpscare9.png} is shipped); {@code 120} is the
 * dedicated travel-jumpscare image and is not part of the random pool.
 */
public final class Jumpscares {

    /** Every random-pool jumpscare image index that has a matching {@code jumpscareN.png}. */
    public static final int[] IMAGES = {1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12};

    private Jumpscares() {
    }

    /** A random valid jumpscare image index. */
    public static int randomImage(Random random) {
        return IMAGES[random.nextInt(IMAGES.length)];
    }

    /** Whether {@code index} is a valid, shipped jumpscare image. */
    public static boolean isValid(int index) {
        if (index == 120) {
            return true; // the travel image
        }
        for (int i : IMAGES) {
            if (i == index) {
                return true;
            }
        }
        return false;
    }
}
