package ru.daniel.soviethifi.audio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaybackCursorTest {
    @Test void jitterAndNetworkCorrectionsDoNotSkipOrRepeatSamples() {
        var cursor = new PlaybackCursor();
        double previous = -1;
        for (int block = 0; block < 500; block++) {
            // Bursty writes and periodic 50 ms server-clock corrections.
            double jitter = block == 0 ? 0 : ((block % 4) - 2) * 512 + (block % 17 == 0 ? 2205 : 0);
            cursor.align(block * 512.0 + jitter, 44100, 44100);
            for (int i = 0; i < 512; i++) {
                double frame = cursor.next();
                if (previous >= 0) assertEquals(1, frame - previous, 0.001001);
                previous = frame;
            }
        }
    }

    @Test void sineWaveHasNoExtraBoundaryClicksUnderJitter() {
        var cursor = new PlaybackCursor();
        double previous = 0, maxJump = 0, legacyJump = 0, legacyPrevious = 0;
        for (int block = 0; block < 200; block++) {
            double target = block * 512.0 + (block == 0 ? 0 : (block % 3 - 1) * 150);
            cursor.align(target, 44100, 44100);
            for (int i = 0; i < 512; i++) {
                double sample = .8 * Math.sin(2 * Math.PI * 1000 * cursor.next() / 44100);
                double legacy = .8 * Math.sin(2 * Math.PI * 1000 * (target + i) / 44100);
                maxJump = Math.max(maxJump, Math.abs(sample - previous));
                legacyJump = Math.max(legacyJump, Math.abs(legacy - legacyPrevious));
                previous = sample;
                legacyPrevious = legacy;
            }
        }
        assertTrue(maxJump < .115, "Continuous sine derivative bound, including block boundaries");
        assertTrue(legacyJump > 1, "The old clock reproduces large discontinuities with this jitter");
    }

    @Test void startsAtLateJoinPositionAndResamples48k() {
        var cursor = new PlaybackCursor();
        cursor.align(48_000 * 45.5, 48000, 44100);
        assertEquals(48_000 * 45.5, cursor.next(), 1e-8);
        assertEquals(48_000 * 45.5 + 48000.0 / 44100, cursor.next(), 1e-8);
    }

    @Test void driftConvergesWithoutSeeking() {
        var cursor = new PlaybackCursor();
        cursor.align(0, 44100, 44100);
        double frame = cursor.next();
        for (int block = 0; block < 3000; block++) {
            cursor.align(1 + block * 512.0 + 441, 44100, 44100);
            for (int i = 0; i < 512; i++) frame = cursor.next();
        }
        double remaining = 3000 * 512.0 + 441 - frame;
        assertTrue(remaining >= 0 && remaining < 20);
    }

    @Test void summedFullScaleSpeakersHaveHeadroom() {
        for (int speakers = 1; speakers <= 32; speakers++) {
            float left = speakers * .8f, right = speakers * .3f;
            float gain = AudioMath.mixHeadroom(left, right);
            assertTrue(left * gain <= .900001f);
            assertTrue(right * gain <= .900001f);
            assertEquals(left / right, (left * gain) / (right * gain), .00001);
        }
    }
}
