package com.example.samplestickerapp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WebpAnimationMuxerTest {

    @Test
    public void writesContainerHeaders() throws IOException {
        final byte[] output = WebpAnimationMuxer.mux(Arrays.asList(frame(lossyFile(), 0, 0, 512, 512, 100)), 512, 512);

        assertEquals("RIFF", fourCc(output, 0));
        assertEquals(output.length - 8, uint(output, 4, 4));
        assertEquals("WEBP", fourCc(output, 8));
        assertEquals("VP8X", fourCc(output, 12));
        assertEquals(0x12, output[20] & 0xFF);
        assertEquals(511, uint(output, 24, 3));
        assertEquals(511, uint(output, 27, 3));
        assertEquals("ANIM", fourCc(output, 30));
        assertEquals(0, uint(output, 42, 2));
        assertEquals("ANMF", fourCc(output, 44));
    }

    @Test
    public void writesFrameGeometryAndDuration() throws IOException {
        final byte[] output = WebpAnimationMuxer.mux(Arrays.asList(frame(lossyFile(), 10, 20, 31, 41, 250)), 512, 512);
        final int frame = 52;

        assertEquals(5, uint(output, frame, 3));
        assertEquals(10, uint(output, frame + 3, 3));
        assertEquals(30, uint(output, frame + 6, 3));
        assertEquals(40, uint(output, frame + 9, 3));
        assertEquals(250, uint(output, frame + 12, 3));
        assertEquals(0x02, output[frame + 15] & 0xFF);
    }

    @Test
    public void keepsAlphaAndBitstreamButDropsOtherChunks() throws IOException {
        final byte[] alpha = chunk("ALPH", new byte[]{1, 2, 3});
        final byte[] bitstream = chunk("VP8 ", new byte[]{4, 5, 6, 7});
        final byte[] file = riff(chunk("VP8X", new byte[10]), alpha, bitstream, chunk("EXIF", new byte[]{9}));

        final byte[] output = WebpAnimationMuxer.mux(Arrays.asList(frame(file, 0, 0, 1, 1, 10)), 1, 1);

        final int payload = 44 + 8 + 16;
        assertArrayEquals(concat(alpha, bitstream), Arrays.copyOfRange(output, payload, output.length));
        assertEquals(16 + alpha.length + bitstream.length, uint(output, 48, 4));
    }

    @Test
    public void paddedChunksKeepEveryFrameAligned() throws IOException {
        final List<WebpAnimationMuxer.Frame> frames = new ArrayList<>();
        frames.add(frame(riff(chunk("VP8L", new byte[]{1, 2, 3})), 0, 0, 1, 1, 10));
        frames.add(frame(lossyFile(), 0, 0, 1, 1, 20));

        final byte[] output = WebpAnimationMuxer.mux(frames, 1, 1);

        final int second = 44 + 8 + uint(output, 48, 4);
        assertEquals(0, second % 2);
        assertEquals("ANMF", fourCc(output, second));
        assertEquals(20, uint(output, second + 8 + 12, 3));
    }

    @Test
    public void rejectsInvalidInput() {
        assertThrows(IOException.class, () -> WebpAnimationMuxer.mux(Arrays.asList(frame(new byte[]{1, 2, 3}, 0, 0, 1, 1, 10)), 1, 1));
        assertThrows(IOException.class, () -> WebpAnimationMuxer.mux(Arrays.asList(frame(riff(chunk("EXIF", new byte[2])), 0, 0, 1, 1, 10)), 1, 1));
        assertThrows(IllegalArgumentException.class, () -> frame(lossyFile(), 1, 0, 1, 1, 10));
    }

    private static WebpAnimationMuxer.Frame frame(byte[] webp, int x, int y, int width, int height, int duration) {
        return new WebpAnimationMuxer.Frame(webp, x, y, width, height, duration);
    }

    private static byte[] lossyFile() {
        return riff(chunk("VP8 ", new byte[]{1, 2}));
    }

    private static byte[] riff(byte[]... chunks) {
        final byte[] body = concat(chunks);
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write('R');
        output.write('I');
        output.write('F');
        output.write('F');
        writeInt(output, body.length + 4);
        output.write('W');
        output.write('E');
        output.write('B');
        output.write('P');
        output.write(body, 0, body.length);
        return output.toByteArray();
    }

    private static byte[] chunk(String name, byte[] payload) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        output.write(nameBytes, 0, nameBytes.length);
        writeInt(output, payload.length);
        output.write(payload, 0, payload.length);
        if (payload.length % 2 == 1) {
            output.write(0);
        }
        return output.toByteArray();
    }

    private static void writeInt(ByteArrayOutputStream output, int value) {
        for (int i = 0; i < 4; i++) {
            output.write((value >> (8 * i)) & 0xFF);
        }
    }

    private static byte[] concat(byte[]... parts) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            output.write(part, 0, part.length);
        }
        return output.toByteArray();
    }

    private static String fourCc(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
    }

    private static int uint(byte[] bytes, int offset, int count) {
        int value = 0;
        for (int i = 0; i < count; i++) {
            value |= (bytes[offset + i] & 0xFF) << (8 * i);
        }
        return value;
    }
}
