package net.pitan76.assetbridge.shape;

/**
 * One axis-aligned box of a block's shape, in the pixel coordinates a model uses:
 * 0 to 16 over the block, y upwards.
 *
 * <p>Deliberately free of Minecraft types, so the geometry can be worked out — and tested —
 * without the game. Turning a list of these into a {@code VoxelShape} is the block layer's job.
 */
public class ShapeBox {
    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;

    public ShapeBox(double x1, double y1, double z1, double x2, double y2, double z2) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    /** A box clamped into the block, or {@code null} when nothing of it is left. */
    public ShapeBox clamped() {
        double x1 = clamp(minX);
        double y1 = clamp(minY);
        double z1 = clamp(minZ);
        double x2 = clamp(maxX);
        double y2 = clamp(maxY);
        double z2 = clamp(maxZ);
        if (x2 - x1 <= 0 || y2 - y1 <= 0 || z2 - z1 <= 0) return null;
        return new ShapeBox(x1, y1, z1, x2, y2, z2);
    }

    /** True for a box that fills the whole block, which is the default shape anyway. */
    public boolean isFullCube() {
        return minX <= 0 && minY <= 0 && minZ <= 0 && maxX >= 16 && maxY >= 16 && maxZ >= 16;
    }

    /**
     * This box turned around the block's vertical centre line, as a blockstate variant's
     * {@code y} does to the model it names.
     *
     * @param degrees a multiple of 90; anything else is returned unrotated, because a shape
     *                that is not axis aligned cannot be expressed as boxes
     */
    public ShapeBox rotateY(int degrees) {
        switch (normalise(degrees)) {
            case 90:
                return new ShapeBox(16 - maxZ, minY, minX, 16 - minZ, maxY, maxX);
            case 180:
                return new ShapeBox(16 - maxX, minY, 16 - maxZ, 16 - minX, maxY, 16 - minZ);
            case 270:
                return new ShapeBox(minZ, minY, 16 - maxX, maxZ, maxY, 16 - minX);
            default:
                return this;
        }
    }

    /** The same for a variant's {@code x}, which tips the model over towards the north. */
    public ShapeBox rotateX(int degrees) {
        switch (normalise(degrees)) {
            case 90:
                return new ShapeBox(minX, minZ, 16 - maxY, maxX, maxZ, 16 - minY);
            case 180:
                return new ShapeBox(minX, 16 - maxY, 16 - maxZ, maxX, 16 - minY, 16 - minZ);
            case 270:
                return new ShapeBox(minX, 16 - maxZ, minY, maxX, 16 - minZ, maxY);
            default:
                return this;
        }
    }

    public ShapeBox union(ShapeBox other) {
        return new ShapeBox(Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
    }

    private static int normalise(int degrees) {
        int wrapped = degrees % 360;
        if (wrapped < 0) wrapped += 360;
        // Only quarter turns keep a box a box; the caller is told nothing happened otherwise.
        return wrapped % 90 == 0 ? wrapped : -1;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(16, value));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ShapeBox)) return false;
        ShapeBox other = (ShapeBox) obj;
        return minX == other.minX && minY == other.minY && minZ == other.minZ
                && maxX == other.maxX && maxY == other.maxY && maxZ == other.maxZ;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(minX);
        result = 31 * result + Double.hashCode(minY);
        result = 31 * result + Double.hashCode(minZ);
        result = 31 * result + Double.hashCode(maxX);
        result = 31 * result + Double.hashCode(maxY);
        result = 31 * result + Double.hashCode(maxZ);
        return result;
    }

    @Override
    public String toString() {
        return "ShapeBox{min:(" + minX + ", " + minY + ", " + minZ + "), max:(" + maxX + ", " + maxY + ", " + maxZ + ")}";
    }
}
