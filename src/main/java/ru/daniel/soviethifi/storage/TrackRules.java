package ru.daniel.soviethifi.storage;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
public final class TrackRules {
    public static final int CHUNK = 16384, MAX_BYTES = 16 * 1024 * 1024, MAX_SECONDS = 360;
    public static final long LIBRARY_BYTES = 256L * 1024 * 1024;
    private TrackRules() {}
    public static boolean validId(String id) { return id != null && id.matches("[0-9a-f]{64}"); }
    public static String hash(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static String title(String text) {
        String clean = text.replaceAll("[\\p{Cntrl}§]", "").strip();
        return clean.isBlank() ? "Кассета" : clean.substring(0, Math.min(80, clean.length()));
    }
    public static boolean audioHeader(byte[] data, String format) {
        if (data.length < 32) return false;
        if (format.equals("ogg")) return data[0] == 'O' && data[1] == 'g' && data[2] == 'g' && data[3] == 'S';
        if (!format.equals("mp3")) return false;
        return (data[0] == 'I' && data[1] == 'D' && data[2] == '3') || ((data[0] & 255) == 255 && (data[1] & 224) == 224);
    }
}
