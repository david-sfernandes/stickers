package com.example.samplestickerapp;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Builds an animated WebP container out of static WebP files, such as the ones produced by
 * Bitmap.compress(). The image data is copied as is, so no native encoder is needed.
 * See https://developers.google.com/speed/webp/docs/riff_container
 */
final class WebpAnimationMuxer {
    private static final int RIFF_HEADER_SIZE = 12;
    private static final int CHUNK_HEADER_SIZE = 8;
    private static final int ANMF_HEADER_SIZE = 16;
    private static final int VP8X_FLAG_ALPHA = 0x10;
    private static final int VP8X_FLAG_ANIMATION = 0x02;
    /* Pixels of the frame replace the canvas, which also allows a frame to become transparent. */
    private static final int ANMF_FLAG_NO_BLEND = 0x02;

    static final class Frame {
        final byte[] webp;
        final int x;
        final int y;
        final int width;
        final int height;
        int durationMs;

        Frame(@NonNull byte[] webp, int x, int y, int width, int height, int durationMs) {
            if (x % 2 != 0 || y % 2 != 0) {
                throw new IllegalArgumentException("frame offsets must be even");
            }
            this.webp = webp;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.durationMs = durationMs;
        }
    }

    private WebpAnimationMuxer() {
    }

    @NonNull
    static byte[] mux(@NonNull List<Frame> frames, int canvasWidth, int canvasHeight) throws IOException {
        final ByteArrayOutputStream body = new ByteArrayOutputStream();

        final ByteArrayOutputStream vp8x = new ByteArrayOutputStream();
        vp8x.write(VP8X_FLAG_ALPHA | VP8X_FLAG_ANIMATION);
        writeLittleEndian(vp8x, 0, 3);
        writeLittleEndian(vp8x, canvasWidth - 1, 3);
        writeLittleEndian(vp8x, canvasHeight - 1, 3);
        writeChunk(body, "VP8X", vp8x.toByteArray());

        final ByteArrayOutputStream anim = new ByteArrayOutputStream();
        writeLittleEndian(anim, 0, 4); // transparent background
        writeLittleEndian(anim, 0, 2); // loop forever
        writeChunk(body, "ANIM", anim.toByteArray());

        for (Frame frame : frames) {
            final ByteArrayOutputStream anmf = new ByteArrayOutputStream();
            writeLittleEndian(anmf, frame.x / 2, 3);
            writeLittleEndian(anmf, frame.y / 2, 3);
            writeLittleEndian(anmf, frame.width - 1, 3);
            writeLittleEndian(anmf, frame.height - 1, 3);
            writeLittleEndian(anmf, frame.durationMs, 3);
            anmf.write(ANMF_FLAG_NO_BLEND);
            copyImageChunks(frame.webp, anmf);
            writeChunk(body, "ANMF", anmf.toByteArray());
        }

        final ByteArrayOutputStream output = new ByteArrayOutputStream(body.size() + RIFF_HEADER_SIZE);
        output.write(fourCc("RIFF"));
        writeLittleEndian(output, body.size() + 4, 4);
        output.write(fourCc("WEBP"));
        body.writeTo(output);
        return output.toByteArray();
    }

    /**
     * Copies the chunks an ANMF frame is allowed to carry: the optional alpha and the bitstream.
     */
    private static void copyImageChunks(@NonNull byte[] webp, @NonNull ByteArrayOutputStream output) throws IOException {
        if (webp.length < RIFF_HEADER_SIZE || !hasFourCc(webp, 0, "RIFF") || !hasFourCc(webp, 8, "WEBP")) {
            throw new IOException("not a webp file");
        }
        boolean foundBitstream = false;
        int offset = RIFF_HEADER_SIZE;
        while (offset + CHUNK_HEADER_SIZE <= webp.length) {
            final int payloadSize = readLittleEndian(webp, offset + 4);
            final int paddedSize = CHUNK_HEADER_SIZE + payloadSize + (payloadSize & 1);
            if (payloadSize < 0 || offset + CHUNK_HEADER_SIZE + payloadSize > webp.length) {
                throw new IOException("truncated webp chunk");
            }
            final boolean isBitstream = hasFourCc(webp, offset, "VP8 ") || hasFourCc(webp, offset, "VP8L");
            if (isBitstream || hasFourCc(webp, offset, "ALPH")) {
                output.write(webp, offset, Math.min(paddedSize, webp.length - offset));
                if ((payloadSize & 1) == 1 && offset + paddedSize > webp.length) {
                    output.write(0);
                }
                foundBitstream |= isBitstream;
            }
            offset += paddedSize;
        }
        if (!foundBitstream) {
            throw new IOException("webp file has no image data");
        }
    }

    private static void writeChunk(@NonNull ByteArrayOutputStream output, @NonNull String name, @NonNull byte[] payload) throws IOException {
        output.write(fourCc(name));
        writeLittleEndian(output, payload.length, 4);
        output.write(payload);
        if ((payload.length & 1) == 1) {
            output.write(0);
        }
    }

    private static void writeLittleEndian(@NonNull ByteArrayOutputStream output, int value, int byteCount) {
        for (int i = 0; i < byteCount; i++) {
            output.write((value >> (8 * i)) & 0xFF);
        }
    }

    private static int readLittleEndian(@NonNull byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | (bytes[offset + 1] & 0xFF) << 8 | (bytes[offset + 2] & 0xFF) << 16 | (bytes[offset + 3] & 0xFF) << 24;
    }

    private static boolean hasFourCc(@NonNull byte[] bytes, int offset, @NonNull String name) {
        final byte[] expected = fourCc(name);
        for (int i = 0; i < expected.length; i++) {
            if (bytes[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    private static byte[] fourCc(@NonNull String name) {
        return name.getBytes(StandardCharsets.US_ASCII);
    }
}
