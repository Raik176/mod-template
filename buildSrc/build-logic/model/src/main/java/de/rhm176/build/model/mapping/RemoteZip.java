package de.rhm176.build.model.mapping;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

final class RemoteZip {
    private static final int TAIL_BYTES = 1 << 16;
    private static final int MAX_DIRECTORY_BYTES = 64 << 20;
    private static final int MAX_ENTRY_BYTES = 16 << 20;
    private static final long END_OF_DIRECTORY = 0x06054b50L;
    private static final long ZIP64_LOCATOR = 0x07064b50L;
    private static final long DIRECTORY_ENTRY = 0x02014b50L;
    private static final long LOCAL_ENTRY = 0x04034b50L;
    private static final long MAXED = 0xFFFFFFFFL;

    private static final int ABSENT = -1;
    private static final int MALFORMED = -2;

    private final String url;
    private final byte[] directory;
    private final ByteBuffer d;

    private RemoteZip(String url, byte[] directory) {
        this.url = url;
        this.directory = directory;
        this.d = le(directory);
    }

    static byte[] readEntry(String url, String name) throws IOException {
        RemoteZip zip = open(url);
        return zip == null ? null : zip.read(name);
    }

    static RemoteZip open(String url) throws IOException {
        Http.Slice tail = Http.range(url, "-" + TAIL_BYTES);
        if (tail == null) return null;

        byte[] tailBytes = tail.bytes();
        int eocd = findEndOfDirectory(tailBytes);
        if (eocd < 0) return null;
        ByteBuffer t = le(tailBytes);
        if (eocd >= 20 && u32(t, eocd - 20) == ZIP64_LOCATOR) return null;
        long directorySize = u32(t, eocd + 12);
        long directoryOffset = u32(t, eocd + 16);
        if (u16(t, eocd + 10) == 0xFFFF || directorySize == MAXED || directoryOffset == MAXED) return null;
        if (directorySize > MAX_DIRECTORY_BYTES || directoryOffset + directorySize > tail.start() + eocd) return null;

        byte[] directory = directory(url, tail, directoryOffset, (int) directorySize);
        return directory == null ? null : new RemoteZip(url, directory);
    }

    boolean contains(String name) {
        return find(name) >= 0;
    }

    byte[] read(String name) throws IOException {
        int pos = find(name);
        if (pos == MALFORMED) return null;
        if (pos == ABSENT) throw new IOException("The archive contains no " + name);
        return entry(
                name,
                u16(d, pos + 8),
                u16(d, pos + 10),
                u32(d, pos + 20),
                u32(d, pos + 24),
                u32(d, pos + 42),
                u16(d, pos + 28) + u16(d, pos + 30));
    }

    private int find(String name) {
        byte[] wanted = name.getBytes(StandardCharsets.UTF_8);
        for (int pos = 0; pos + 46 <= directory.length; ) {
            if (u32(d, pos) != DIRECTORY_ENTRY) return MALFORMED;
            int nameLength = u16(d, pos + 28);
            int extraLength = u16(d, pos + 30);
            int commentLength = u16(d, pos + 32);
            if (pos + 46 + nameLength > directory.length) return MALFORMED;
            if (Arrays.equals(directory, pos + 46, pos + 46 + nameLength, wanted, 0, wanted.length)) return pos;
            pos += 46 + nameLength + extraLength + commentLength;
        }
        return ABSENT;
    }

    private byte[] entry(String name, int flags, int method, long packed, long size, long offset, int variableLength)
            throws IOException {
        if ((flags & 1) != 0 || packed == MAXED || size == MAXED || offset == MAXED) return null;
        if (packed > MAX_ENTRY_BYTES || size > MAX_ENTRY_BYTES) throw new IOException(name + " is unexpectedly large");
        if (method != 0 && method != 8) return null;

        long guess = 30L + variableLength + packed;
        Http.Slice local = Http.range(url, offset + "-" + (offset + guess - 1));
        if (local == null) return null;
        byte[] bytes = local.bytes();
        if (bytes.length < 30 || u32(le(bytes), 0) != LOCAL_ENTRY) throw new IOException("Corrupt entry " + name);
        ByteBuffer l = le(bytes);
        long dataStart = 30L + u16(l, 26) + u16(l, 28);
        long end = dataStart + packed;
        if (bytes.length < end) {
            Http.Slice rest = Http.range(url, (offset + bytes.length) + "-" + (offset + end - 1));
            if (rest == null) return null;
            bytes = concat(bytes, rest.bytes());
            if (bytes.length < end) throw new IOException("Truncated entry " + name);
        }

        byte[] data = Arrays.copyOfRange(bytes, (int) dataStart, (int) end);
        if (method == 0) {
            if (data.length != size) throw new IOException("Corrupt entry " + name);
            return data;
        }
        return inflate(data, (int) size, name);
    }

    private static byte[] directory(String url, Http.Slice tail, long offset, int size) throws IOException {
        byte[] tailBytes = tail.bytes();
        if (offset >= tail.start()) {
            int from = (int) (offset - tail.start());
            return Arrays.copyOfRange(tailBytes, from, from + size);
        }
        Http.Slice head = Http.range(url, offset + "-" + (tail.start() - 1));
        if (head == null || head.start() != offset) return null;
        byte[] joined = concat(head.bytes(), tailBytes);
        return joined.length < size ? null : Arrays.copyOf(joined, size);
    }

    private static int findEndOfDirectory(byte[] tail) {
        ByteBuffer b = le(tail);
        for (int i = tail.length - 22; i >= 0; i--) {
            if (u32(b, i) == END_OF_DIRECTORY && i + 22 + u16(b, i + 20) == tail.length) return i;
        }
        return -1;
    }

    private static byte[] inflate(byte[] data, int size, String name) throws IOException {
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(data);
            byte[] out = new byte[size];
            int n = 0;
            while (n < size) {
                int read = inflater.inflate(out, n, size - n);
                if (read == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) break;
                n += read;
            }
            if (n != size) throw new IOException("Corrupt entry " + name);
            return out;
        } catch (DataFormatException e) {
            throw new IOException("Corrupt entry " + name, e);
        } finally {
            inflater.end();
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static ByteBuffer le(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static int u16(ByteBuffer b, int at) {
        return Short.toUnsignedInt(b.getShort(at));
    }

    private static long u32(ByteBuffer b, int at) {
        return Integer.toUnsignedLong(b.getInt(at));
    }
}
