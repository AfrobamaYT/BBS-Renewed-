package com.afrobama.bbstools.hitbox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Decoupled bridge between the film-side BBS clip (where the keyframed
 * hitbox values are produced per render-tick) and the server-side damage
 * tick handler (where the AABB intersects entities and applies damage).
 *
 * Each entry: {x, y, z, sizeX, sizeY, sizeZ, damage, cooldownTicks(optional)}
 * Ported from NeoForge BBSTools.
 */
public final class FilmHitboxBridge
{
    public static final List<double[]> activeBoxes = Collections.synchronizedList(new ArrayList<>());

    /** Snapshot for client-side rendering (copied before activeBoxes is cleared each tick). */
    public static volatile List<double[]> lastFrameBoxes = Collections.emptyList();

    /** Wireframe only visible when toggled on via /hitbox show. */
    public static volatile boolean showWireframe = false;

    private FilmHitboxBridge()
    {
    }

}
