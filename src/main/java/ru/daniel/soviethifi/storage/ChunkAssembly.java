package ru.daniel.soviethifi.storage;
import java.io.ByteArrayOutputStream;
/** Sequential, bounded assembler. Invalid chunks never change its state. */
public final class ChunkAssembly {
    private final int size;
    private final ByteArrayOutputStream bytes;
    public ChunkAssembly(int size) {
        if (size < 32 || size > TrackRules.MAX_BYTES) throw new IllegalArgumentException("Invalid track size");
        this.size = size;
        bytes = new ByteArrayOutputStream(Math.min(size, 65536));
    }
    public void append(int offset, byte[] chunk) {
        if (offset != bytes.size() || chunk.length == 0 || chunk.length > TrackRules.CHUNK || (long)offset + chunk.length > size)
            throw new IllegalArgumentException("Invalid chunk sequence");
        bytes.writeBytes(chunk);
    }
    public boolean complete() { return bytes.size() == size; }
    public int received() { return bytes.size(); }
    public byte[] finish(String expectedHash) {
        if (!complete()) throw new IllegalStateException("Incomplete track");
        byte[] data = bytes.toByteArray();
        if (!TrackRules.hash(data).equals(expectedHash)) throw new IllegalArgumentException("Track checksum mismatch");
        return data;
    }
}
