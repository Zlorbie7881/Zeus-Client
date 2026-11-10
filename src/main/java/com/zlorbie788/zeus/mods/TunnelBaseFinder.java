package com.zlorbie788.zeus.mods;

/**
 * Focused underground variant of the shared base-scoring scanner. It reuses the same
 * player-build evidence and natural-structure filtering, but starts in underground-only
 * mode so ordinary surface builds do not dominate its results.
 */
public final class TunnelBaseFinder extends SuspiciousChunks {
    public TunnelBaseFinder() {
        super("tunnelbasefinder", "Tunnel Base Finder",
                "Finds underground bases using player-build clues plus enclosed tunnel and shaft geometry. Caves alone are not flagged; only already-loaded chunks are inspected.",
                2, 1, 1);
    }

    @Override
    protected boolean useTunnelGeometry() {
        return true;
    }
}
