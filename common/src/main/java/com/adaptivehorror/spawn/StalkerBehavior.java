package com.adaptivehorror.spawn;

/**
 * Where the single managed stalker spawned, which also decides how the player triggers it:
 * <ul>
 *   <li>{@link #BEHIND} - directly behind the player; triggered by looking at it.</li>
 *   <li>{@link #FAR} - 75-175 blocks off; triggered by approaching within the vanish radius.</li>
 *   <li>{@link #WINDOW} - at night, just outside a sheltered player's window; triggered by either.</li>
 *   <li>{@link #FRONT_SLEEP} - right in front of a sleeping player; vanishes when they wake.</li>
 * </ul>
 * On trigger the stalker almost always just vanishes; rarely (config) it strikes.
 */
public enum StalkerBehavior {
    BEHIND,
    FAR,
    WINDOW,
    FRONT_SLEEP
}
