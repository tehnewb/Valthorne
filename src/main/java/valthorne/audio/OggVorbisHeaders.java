package valthorne.audio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Checks Vorbis identification and comment packet boundaries before native decoding.
 * Invalid comment counts can otherwise make the native decoder free uninitialized
 * memory. Validation reads only the two metadata packets, including packets spanning
 * Ogg pages, and leaves setup/audio decoding to the codec. Metadata packets are
 * bounded to 16 MiB to prevent unbounded allocation from malformed input. No audio
 * samples or native resources are allocated. Instances are confined to one load.
 */
final class OggVorbisHeaders {
    /**
     * Maximum supported size of either metadata packet.
     */
    private static final int MAX_PACKET_BYTES = 16 * 1024 * 1024;

    private final InputStream input; // Encoded stream borrowed for metadata validation.
    private byte[] segments = new byte[0]; // Current page's lacing values.
    private int segment; // Next lacing entry in the current page.

    /**
     * Creates a parser borrowing an already opened encoded stream.
     * @param input encoded Ogg bytes
     */
    private OggVorbisHeaders(InputStream input) {
        this.input = input;
    }

    /**
     * Checks memory-backed metadata before handing the bytes to a native decoder.
     * @param bytes complete encoded content
     */
    static void validate(byte[] bytes) {
        try {
            new OggVorbisHeaders(new ByteArrayInputStream(bytes)).validate();
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invalid Ogg Vorbis headers", failure);
        }
    }

    /**
     * Reads only the metadata prefix of a file, without buffering its audio payload.
     * @param path encoded file
     */
    static void validate(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            new OggVorbisHeaders(input).validate();
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invalid Ogg Vorbis headers", failure);
        }
    }

    /**
     * Validates the identification fields and all comment lengths before native use.
     * @throws IOException if a packet is truncated or structurally invalid
     */
    private void validate() throws IOException {
        byte[] identification = packet();
        signature(identification, 1);
        if (identification.length != 30 || integer(identification, 7) != 0 || identification[11] == 0 || integer(identification, 12) == 0 || identification[29] != 1)
            throw new IOException("Invalid Vorbis identification packet");
        int small = identification[28] & 15;
        int large = (identification[28] & 255) >>> 4;
        if (small < 6 || large > 13 || small > large)
            throw new IOException("Invalid Vorbis block sizes");
        byte[] comments = packet();
        signature(comments, 3);
        int offset = end(comments, 7);
        long count = integer(comments, offset);
        offset += 4;
        if (count > (comments.length - offset) / 4)
            throw new IOException("Vorbis comment count exceeds packet bounds");
        for (long i = 0; i < count; i++) offset = end(comments, offset);
        if (offset >= comments.length || (comments[offset] & 1) == 0)
            throw new IOException("Missing Vorbis comment framing bit");
    }

    /**
     * Reads a length-prefixed string boundary without narrowing an unsigned length.
     * @param bytes comment packet
     * @param offset offset of the four-byte length
     * @return first byte after the string
     * @throws IOException if its length exceeds packet bounds
     */
    private static int end(byte[] bytes, int offset) throws IOException {
        long length = integer(bytes, offset);
        long end = offset + 4L + length;
        if (end > bytes.length) throw new IOException("Vorbis comment exceeds packet bounds");
        return (int) end;
    }

    /**
     * Reads an unsigned little-endian length with an explicit bounds check.
     * @param bytes packet bytes
     * @param offset integer offset
     * @return unsigned value
     * @throws IOException if the field is truncated
     */
    private static long integer(byte[] bytes, int offset) throws IOException {
        if (offset < 0 || offset > bytes.length - 4) throw new IOException("Truncated Vorbis length");
        return (bytes[offset] & 255L) | (bytes[offset + 1] & 255L) << 8 | (bytes[offset + 2] & 255L) << 16 | (bytes[offset + 3] & 255L) << 24;
    }

    /**
     * Checks a metadata packet's type and Vorbis signature.
     * @param bytes metadata packet
     * @param type expected packet type
     * @throws IOException if the signature is invalid
     */
    private static void signature(byte[] bytes, int type) throws IOException {
        if (bytes.length < 7 || bytes[0] != type || bytes[1] != 'v' || bytes[2] != 'o' || bytes[3] != 'r' || bytes[4] != 'b' || bytes[5] != 'i' || bytes[6] != 's')
            throw new IOException("Invalid Vorbis metadata signature");
    }

    /**
     * Reassembles a bounded packet using Ogg page lacing, including continuations.
     * @return one complete metadata packet
     * @throws IOException if its pages or payload are malformed or truncated
     */
    private byte[] packet() throws IOException {
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        boolean continuing = false;
        while (true) {
            if (segment == segments.length) {
                byte[] header = input.readNBytes(27);
                if (header.length != 27 || header[0] != 'O' || header[1] != 'g' || header[2] != 'g' || header[3] != 'S' || header[4] != 0 || ((header[5] & 1) != 0) != continuing)
                    throw new IOException("Invalid or truncated Ogg metadata page");
                segments = input.readNBytes(header[26] & 255);
                if (segments.length != (header[26] & 255)) throw new IOException("Truncated Ogg lacing table");
                segment = 0;
                if (segments.length == 0) continue;
            }
            int size = segments[segment++] & 255;
            if (packet.size() > MAX_PACKET_BYTES - size) throw new IOException("Vorbis metadata packet exceeds 16 MiB");
            byte[] payload = input.readNBytes(size);
            if (payload.length != size) throw new IOException("Truncated Ogg metadata packet");
            packet.write(payload);
            if (size < 255) return packet.toByteArray();
            continuing = true;
        }
    }
}
