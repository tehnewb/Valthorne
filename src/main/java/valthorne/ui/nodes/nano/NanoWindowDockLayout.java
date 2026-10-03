package valthorne.ui.nodes.nano;

import valthorne.ui.UINode;
import valthorne.ui.enums.WindowSnapArea;
import valthorne.ui.nodes.nano.NanoWindow.Frame;

/**
 * Resolves nonoverlapping window docks in a three-by-three workspace grid.
 * Left and right bands reserve 30 percent of the workspace; top and bottom
 * bands reserve 35 percent of its usable height, expanding to accommodate the
 * participating windows' minimum sizes. Corners own their intersection,
 * side docks fill the remaining vertical band, and horizontal docks fill the
 * remaining width between occupied side bands. Multiple windows in one region
 * divide that region equally. Floating and hidden windows do not reserve space.
 * Resolution uses the proposed membership for previews as well as commits, with
 * no mutation or temporary collections. All calls belong to the UI thread.
 */
final class NanoWindowDockLayout {
    /**
     * Prevents construction of the stateless region resolver.
     */
    private NanoWindowDockLayout() {}

    /**
     * Computes a region using the proposed membership of a dragged window.
     *
     * @param target window whose region is requested
     * @param proposed window being docked
     * @param proposedArea proposed destination
     * @param inset reserved toolbar height
     * @return allocated rectangle
     */
    static Frame resolve(NanoWindow target, NanoWindow proposed, WindowSnapArea proposedArea, float inset) {
        /*
         * Scan sibling membership once rather than allocate a docking tree for
         * each pointer event. Creation order keeps equal slots stable on focus.
         */
        WindowSnapArea area = target == proposed ? proposedArea : target.getSnapArea();
        int occupied = 0;
        int count = 0;
        int index = 0;
        float leftMinimum = 0;
        float rightMinimum = 0;
        float northMinimum = 0;
        float southMinimum = 0;
        float northWestWidth = 0;
        float northEastWidth = 0;
        float southWestWidth = 0;
        float southEastWidth = 0;
        for (UINode child : target.getParent().getChildren()) {
            if (!(child instanceof NanoWindow window) || (!window.isVisible() && window != proposed) || !window.isSnapGrowing()) continue;
            WindowSnapArea member = window == proposed ? proposedArea : window.getSnapArea();
            occupied |= 1 << member.ordinal();
            switch (member) {
                case WEST -> leftMinimum = Math.max(leftMinimum, window.getMinimumWidth());
                case EAST -> rightMinimum = Math.max(rightMinimum, window.getMinimumWidth());
                case NORTH -> northMinimum = Math.max(northMinimum, window.getMinimumHeight());
                case SOUTH -> southMinimum = Math.max(southMinimum, window.getMinimumHeight());
                case NORTH_WEST -> {
                    northWestWidth += window.getMinimumWidth();
                    northMinimum = Math.max(northMinimum, window.getMinimumHeight());
                }
                case NORTH_EAST -> {
                    northEastWidth += window.getMinimumWidth();
                    northMinimum = Math.max(northMinimum, window.getMinimumHeight());
                }
                case SOUTH_WEST -> {
                    southWestWidth += window.getMinimumWidth();
                    southMinimum = Math.max(southMinimum, window.getMinimumHeight());
                }
                case SOUTH_EAST -> {
                    southEastWidth += window.getMinimumWidth();
                    southMinimum = Math.max(southMinimum, window.getMinimumHeight());
                }
                default -> { }
            }
            if (member == area) {
                count++;
                if (window.getDockOrder() < target.getDockOrder()) index++;
            }
        }
        float width = target.getParent().getWidth();
        float height = target.getParent().getHeight() - inset;
        float leftSize = Math.max(Math.round(width * .3f), Math.max(leftMinimum, Math.max(northWestWidth, southWestWidth)));
        float rightSize = Math.max(Math.round(width * .3f), Math.max(rightMinimum, Math.max(northEastWidth, southEastWidth)));
        float northSize = Math.max(Math.round(height * .35f), northMinimum);
        float southSize = Math.max(Math.round(height * .35f), southMinimum);
        boolean left = contains(occupied, WindowSnapArea.WEST) || contains(occupied, WindowSnapArea.NORTH_WEST) || contains(occupied, WindowSnapArea.SOUTH_WEST);
        boolean right = contains(occupied, WindowSnapArea.EAST) || contains(occupied, WindowSnapArea.NORTH_EAST) || contains(occupied, WindowSnapArea.SOUTH_EAST);
        boolean northOccupied = contains(occupied, WindowSnapArea.NORTH) || contains(occupied, WindowSnapArea.NORTH_WEST) || contains(occupied, WindowSnapArea.NORTH_EAST);
        boolean southOccupied = contains(occupied, WindowSnapArea.SOUTH) || contains(occupied, WindowSnapArea.SOUTH_WEST) || contains(occupied, WindowSnapArea.SOUTH_EAST);
        if ((left ? leftSize : 0) + (right ? rightSize : 0) > width
                || (northOccupied ? northSize : 0) + (southOccupied ? southSize : 0) > height) return new Frame(0, inset, 0, 0);
        float x = 0;
        float y = inset;
        float w = width;
        float h = height;
        switch (area) {
            case WEST, EAST -> {
                boolean east = area == WindowSnapArea.EAST;
                x = east ? width - rightSize : 0;
                w = east ? rightSize : leftSize;
                float top = contains(occupied, east ? WindowSnapArea.NORTH_EAST : WindowSnapArea.NORTH_WEST) ? northSize : 0;
                float bottom = contains(occupied, east ? WindowSnapArea.SOUTH_EAST : WindowSnapArea.SOUTH_WEST) ? southSize : 0;
                y += top;
                h -= top + bottom;
            }
            case NORTH, SOUTH -> {
                x = left ? leftSize : 0;
                w -= (left ? leftSize : 0) + (right ? rightSize : 0);
                h = area == WindowSnapArea.NORTH ? northSize : southSize;
                if (area == WindowSnapArea.SOUTH) y += height - southSize;
            }
            case NORTH_WEST, NORTH_EAST, SOUTH_WEST, SOUTH_EAST -> {
                boolean east = area == WindowSnapArea.NORTH_EAST || area == WindowSnapArea.SOUTH_EAST;
                boolean south = area == WindowSnapArea.SOUTH_WEST || area == WindowSnapArea.SOUTH_EAST;
                w = east ? rightSize : leftSize;
                h = south ? southSize : northSize;
                if (east) x = width - rightSize;
                if (south) y += height - southSize;
            }
            default -> { }
        }
        if (count > 1) {
            if (area == WindowSnapArea.WEST || area == WindowSnapArea.EAST) {
                float start = Math.round(h * index / count);
                float end = Math.round(h * (index + 1) / count);
                y += start;
                h = end - start;
            } else {
                float start = Math.round(w * index / count);
                float end = Math.round(w * (index + 1) / count);
                x += start;
                w = end - start;
            }
        }
        return new Frame(x, y, w, h);
    }

    /**
     * Tests one region's presence in the compact occupancy mask.
     *
     * @param occupied sibling occupancy bits
     * @param area requested region
     * @return whether the region has a visible dock
     */
    private static boolean contains(int occupied, WindowSnapArea area) {
        /*
         * Enum ordinals are private transient bit positions, never persisted.
         */
        return (occupied & (1 << area.ordinal())) != 0;
    }
}
