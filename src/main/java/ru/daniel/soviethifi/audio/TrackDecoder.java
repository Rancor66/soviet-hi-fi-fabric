package ru.daniel.soviethifi.audio;

import javazoom.jl.decoder.*;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import ru.daniel.soviethifi.storage.TrackRules;
import java.io.*;
import java.nio.*;
import java.util.Arrays;

/** Client-only decoding, with a hard PCM allocation limit for untrusted server audio. */
public final class TrackDecoder {
    private static final int MAX_SAMPLES = 48000 * TrackRules.MAX_SECONDS * 2;
    private TrackDecoder() {}
    public static PcmTrack decode(byte[] data, String format) throws Exception {
        if (data.length > TrackRules.MAX_BYTES || !TrackRules.audioHeader(data, format)) throw new IOException("Неподдерживаемый аудиофайл");
        PcmTrack track = format.equals("mp3") ? mp3(data) : ogg(data);
        if (track.seconds() < 0.1 || track.seconds() > TrackRules.MAX_SECONDS) throw new IOException("Трек должен быть не длиннее 6 минут");
        return track;
    }
    private static PcmTrack mp3(byte[] data) throws Exception {
        Bitstream stream = new Bitstream(new ByteArrayInputStream(data));
        Decoder decoder = new Decoder();
        Samples out = new Samples();
        int rate = 0;
        try {
            Header header;
            while ((header = stream.readFrame()) != null) {
                SampleBuffer buffer = (SampleBuffer)decoder.decodeFrame(header, stream);
                if (rate != 0 && rate != buffer.getSampleFrequency()) throw new IOException("Переменная частота дискретизации");
                rate = buffer.getSampleFrequency();
                validateRate(rate, buffer.getChannelCount());
                out.append(buffer.getBuffer(), buffer.getBufferLength(), buffer.getChannelCount());
                stream.closeFrame();
            }
        } finally { stream.close(); }
        if (rate == 0) throw new IOException("Пустой MP3");
        return new PcmTrack(out.finish(), rate);
    }
    private static PcmTrack ogg(byte[] data) throws IOException {
        ByteBuffer input = MemoryUtil.memAlloc(data.length);
        long decoder = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            input.put(data).flip();
            IntBuffer error = stack.mallocInt(1);
            decoder = STBVorbis.stb_vorbis_open_memory(input, error, null);
            if (decoder == 0) throw new IOException("Нужен OGG Vorbis (не Opus)");
            STBVorbisInfo info = STBVorbisInfo.malloc(stack);
            STBVorbis.stb_vorbis_get_info(decoder, info);
            int rate = info.sample_rate(), channels = info.channels();
            validateRate(rate, channels);
            Samples out = new Samples();
            ShortBuffer buffer = stack.mallocShort(8192);
            short[] chunk = new short[8192];
            int frames;
            while ((frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, channels, buffer)) > 0) {
                buffer.get(0, chunk, 0, frames * channels);
                out.append(chunk, frames * channels, channels);
            }
            return new PcmTrack(out.finish(), rate);
        } finally {
            if (decoder != 0) STBVorbis.stb_vorbis_close(decoder);
            MemoryUtil.memFree(input);
        }
    }
    private static void validateRate(int rate, int channels) throws IOException {
        if (rate < 8000 || rate > 48000 || channels < 1 || channels > 2) throw new IOException("Нужен mono/stereo, 8–48 кГц");
    }
    private static final class Samples {
        short[] data = new short[65536];
        int size;
        void append(short[] source, int length, int channels) throws IOException {
            int added = length * (channels == 1 ? 2 : 1);
            if ((long)size + added > MAX_SAMPLES) throw new IOException("Превышен лимит декодированного звука");
            if (size + added > data.length) data = Arrays.copyOf(data, Math.min(MAX_SAMPLES, Math.max(size + added, data.length * 2)));
            if (channels == 2) { System.arraycopy(source, 0, data, size, length); size += length; }
            else for (int i = 0; i < length; i++) { data[size++] = source[i]; data[size++] = source[i]; }
        }
        short[] finish() { return Arrays.copyOf(data, size); }
    }
}
