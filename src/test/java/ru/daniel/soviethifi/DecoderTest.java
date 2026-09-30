package ru.daniel.soviethifi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import ru.daniel.soviethifi.audio.TrackDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DecoderTest {
    private void verify(String format) throws Exception {
        var fixture = Path.of("demo/Dacha-90." + format);
        Assumptions.assumeTrue(Files.isRegularFile(fixture), "Optional local audio fixture is unavailable");
        var pcm = TrackDecoder.decode(Files.readAllBytes(fixture), format);
        assertEquals(44100, pcm.sampleRate());
        assertEquals(48, pcm.seconds(), 0.1);
        long energy = 0, stereoDifference = 0;
        var samples = pcm.samples();
        for (int i = 0; i < samples.length; i += 2) {
            energy += Math.abs((int)samples[i]);
            stereoDifference += Math.abs((int)samples[i] - samples[i + 1]);
        }
        assertTrue(energy > 1_000_000, "Decoded track must contain audible samples");
        assertTrue(stereoDifference > 100_000, "Stereo channels must remain distinct");
    }
    @Test void mp3DecodesFullDurationAndStereo() throws Exception { verify("mp3"); }
    @Test void oggDecodesFullDurationAndStereo() throws Exception { verify("ogg"); }
}
