package valthorne.graphics.map.tiled;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * The TiledDecoding class provides utility methods for decoding layer data from the
 * TMX (Tile Map XML) file format used in tiled map editors. It supports decoding
 * data encoded in CSV or Base64 formats, with optional compression mechanisms such as
 * GZIP and ZLIB.
 */
public class TiledDecoding {

    /**
     * Decodes layer data from the provided textual input using the specified encoding and compression methods.
     * <p>
     * This method supports encoding modes such as "csv" and "base64", and optional compression
     * methods including "gzip" and "zlib". If the input data cannot be decoded due to unsupported
     * encoding or other reasons, an appropriate exception will be thrown.
     *
     * @param text          the text data representing the layer information; may include encoded or compressed data.
     * @param encoding      the type of encoding used for the input text (e.g., "csv", "base64").
     * @param compression   the type of compression applied to the encoded input (e.g., "gzip", "zlib"), or may be null.
     * @param expectedCount the number of expected integers in the output array; used to validate and pad decoded results.
     * @return an array of integers representing the decoded layer data. If the input is empty or null, a zero-filled array is returned.
     * @throws java.io.IOException   if an I/O error occurs during the decoding process.
     * @throws IllegalStateException if an unsupported encoding or compression type is specified.
     */
    public static int[] decodeLayerData(String text, String encoding, String compression, int expectedCount) throws java.io.IOException {
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

    private static int[] decodeLittleEndianU32(byte[] bytes, int expectedCount) {
        int count = (bytes.length / 4);
        int n = expectedCount > 0 ? Math.min(expectedCount, count) : count;

        int[] out = new int[n];
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < n; i++) out[i] = bb.getInt();
        return out;
    }

    private static byte[] readAllBytes(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(4096);
        byte[] buf = new byte[8192];
        int read;
        while ((read = in.read(buf)) != -1) bos.write(buf, 0, read);
        return bos.toByteArray();
    }
}