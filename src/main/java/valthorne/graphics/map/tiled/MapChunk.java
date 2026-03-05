package valthorne.graphics.map.tiled;

public record MapChunk(int x, int y, int width, int height, int[] globalTileIDs) {
    public MapChunk {
        if (globalTileIDs == null)
            globalTileIDs = new int[Math.max(0, width * height)];
    }
}