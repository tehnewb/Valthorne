package valthorne.graphics.model;

/**
 * Indexes immutable interleaved mesh uploads by exact vertex contents. Every attribute
 * participates in equality, so position sharing never erases normal, UV, or color seams.
 * Compaction changes the caller's staging array and produces indices in original vertex
 * order; it neither changes model geometry nor owns native rendering resources.
 *
 * @author Albert Beaupre
 */
final class VertexCompaction3D {
    /**
     * Prevents construction of this stateless upload utility.
     */
    private VertexCompaction3D() {
    }

    /**
     * Compacts complete vertex records into the beginning of the supplied array and writes
     * one index per original vertex. Raw float bits determine equality, preserving signed
     * zero and distinct NaN payloads. The unused array tail is unspecified; upload only the
     * returned vertex count times the stride. Empty input returns zero.
     *
     * @param data    mutable interleaved attribute staging array
     * @param stride  positive number of floats in each complete vertex
     * @param indices destination with exactly one element per original vertex
     * @return number of unique records in the compacted prefix
     * @throws IllegalArgumentException if dimensions disagree or the mesh exceeds indexing capacity
     * @throws NullPointerException     if an array is null
     */
    static int compact(float[] data, int stride, int[] indices) {
        if (stride <= 0 || data.length % stride != 0 || indices.length != data.length / stride)
            throw new IllegalArgumentException("Vertex data and index counts must match");
        int count = indices.length;
        if (count > (1 << 28)) throw new IllegalArgumentException("Mesh exceeds indexing capacity");
        int capacity = 1;
        while (capacity < count * 2) capacity <<= 1;
        int[] table = new int[capacity];
        int unique = 0, mask = capacity - 1;
        for (int vertex = 0; vertex < count; vertex++) {
            int source = vertex * stride, hash = 0x811c9dc5;
            for (int c = 0; c < stride; c++) hash = (hash ^ Float.floatToRawIntBits(data[source + c])) * 0x01000193;
            hash ^= hash >>> 16;
            int slot = hash & mask;
            for (; ; ) {
                int previous = table[slot] - 1;
                if (previous < 0) {
                    System.arraycopy(data, source, data, unique * stride, stride);
                    table[slot] = unique + 1;
                    indices[vertex] = unique++;
                    break;
                }
                boolean equal = true;
                for (int c = 0; c < stride; c++) {
                    if (Float.floatToRawIntBits(data[previous * stride + c]) != Float.floatToRawIntBits(data[source + c])) {
                        equal = false;
                        break;
                    }
                }
                if (equal) {
                    indices[vertex] = previous;
                    break;
                }
                slot = (slot + 1) & mask;
            }
        }
        return unique;
    }
}
