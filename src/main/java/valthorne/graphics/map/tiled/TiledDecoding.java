package valthorne.graphics.map.tiled;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import java.io.IOException;

/**
 * Decodes TMX tile-layer payloads into raw global-ID bit patterns, including Tiled's
 * flip flags. CSV and MIME Base64 are supported; Base64 may additionally use gzip
 * or zlib compression. XML tile elements are not supported.
 * <p>CSV can pad a requested result length with empty tiles. Binary decoding instead
 * returns only complete four-byte IDs that are actually present. Callers must not
 * assume every encoding produces exactly the requested count.
 *
 * @author Albert Beaupre
 */
public class TiledDecoding {

    /**
     * Decodes a layer or chunk payload without clearing the flip bits in its IDs.
     * Empty or null text produces {@code max(0, expectedCount)} zero IDs before encoding
     * validation. CSV ignores the compression argument; Base64 uses optional gzip or
     * zlib decompression. Neither path validates IDs against a tileset.
     *
     * @param text encoded payload, or null for an empty layer
     *
     * @param encoding case-insensitive CSV or Base64 encoding name
     * @param compression Base64 compression name, or null/blank for uncompressed bytes
     * @param expectedCount positive output limit; CSV pads to this length, while binary
     *        data may return fewer IDs; nonpositive values retain all available IDs
     * @return newly allocated array containing signed Java representations of ID bits
     * @throws java.io.IOException if decompression or reading fails
     * @throws IllegalStateException if nonempty data uses an unsupported encoding or compression
     * @throws IllegalArgumentException if numeric CSV or Base64 data is malformed
     */
    public static int[] decodeLayerData(String text, String encoding, String compression, int expectedCount) throws IOException {
        if (text == null) return new int[Math.max(0, expectedCount)];
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return new int[Math.max(0, expectedCount)];

        if (encoding == null || encoding.isBlank() || "xml".equalsIgnoreCase(encoding)) {
            throw new IllegalStateException("TMX layer encoding XML is not implemented. Use CSV or Base64.");
        }

        if ("csv".equalsIgnoreCase(encoding)) {
            return decodeCsv(trimmed, expectedCount);
        }

        if ("base64".equalsIgnoreCase(encoding)) {
            byte[] raw = Base64.getMimeDecoder().decode(trimmed);

            InputStream in = new ByteArrayInputStream(raw);
            if (compression != null && !compression.isBlank()) {
                if ("gzip".equalsIgnoreCase(compression)) {
                    in = new GZIPInputStream(in);
                } else if ("zlib".equalsIgnoreCase(compression)) {
                    in = new InflaterInputStream(in);
                } else {
                    throw new IllegalStateException("Unsupported TMX compression: " + compression);
                }
            }

            byte[] bytes = readAllBytes(in);
            return decodeLittleEndianU32(bytes, expectedCount);
        }

        throw new IllegalStateException("Unsupported TMX encoding: " + encoding);
    }

    /**
     * Parses comma- or whitespace-separated decimal IDs into a new array.
     * Positive expected counts truncate excess values or pad missing values with zero.
     * Numbers are parsed as longs and narrowed to int, retaining their low 32 bits;
     * this preserves unsigned TMX IDs but does not enforce an unsigned 32-bit range.
     *
     * @param csv non-null payload text
     *
     * @param expectedCount requested length, or a nonpositive value to use the token count
     * @return newly allocated array of raw ID bits
     * @throws NumberFormatException if a consumed token is not a valid decimal long
     */
    private static int[] decodeCsv(String csv, int expectedCount) {
        String[] parts = csv.split("[,\\s]+");
        int n = expectedCount > 0 ? expectedCount : parts.length;
        int[] out = new int[n];

        int i = 0;
        for (; i < parts.length && i < n; i++) {
            String p = parts[i].trim();
            if (p.isEmpty()) {
                out[i] = 0;
                continue;
            }
            long v = Long.parseLong(p);
            out[i] = (int) v;
        }
        for (; i < n; i++) out[i] = 0;
        return out;
    }

    /**
     * Reads complete little-endian 32-bit IDs without changing the input bytes.
     * Trailing one to three bytes are ignored. A positive expected count limits the
     * result but never pads it when the byte payload contains fewer complete IDs.
     *
     * @param bytes uncompressed ID bytes
     *
     * @param expectedCount positive maximum ID count, or nonpositive for all complete IDs
     * @return newly allocated array preserving all 32 bits of each consumed ID
     */
    private static int[] decodeLittleEndianU32(byte[] bytes, int expectedCount) {
        int count = (bytes.length / 4);
        int n = expectedCount > 0 ? Math.min(expectedCount, count) : count;

        int[] out = new int[n];
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < n; i++) out[i] = bb.getInt();
        return out;
    }

    /**
     * Consumes the stream from its current position through end of input.
     * The stream remains open; this helper neither resets it nor limits the amount
     * of memory needed for the accumulated payload.
     *
     * @param in stream supplying decoded bytes
     *
     * @return newly allocated array containing all remaining bytes
     * @throws java.io.IOException if a stream read fails
     */
    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(4096);
        byte[] buf = new byte[8192];
        int read;
        while ((read = in.read(buf)) != -1) bos.write(buf, 0, read);
        return bos.toByteArray();
    }
}
