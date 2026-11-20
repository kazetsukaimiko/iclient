package io.freedriver.iclient.http.api.call;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** A stream source. The caller closes each stream and opens a one-shot body only once. */
public interface HttpBody {
    InputStream openStream() throws IOException;
    boolean repeatable();
    OptionalLong contentLength();
    Optional<String> contentType();

    /** Reads the whole body, subject to a byte limit. Use -1 for an unbounded read. */
    default FullyBuffered buffer(int maxBytes) throws IOException {
        if (maxBytes < -1) {
            throw new IllegalArgumentException("Invalid buffer limit: " + maxBytes);
        }
        if (this instanceof FullyBuffered buffered) {
            if (maxBytes >= 0 && buffered.contentLength().orElseThrow() > maxBytes) {
                throw overLimit(maxBytes);
            }
            return buffered;
        }
        try (var stream = openStream()) {
            byte[] bytes = maxBytes == -1 ? stream.readAllBytes() : stream.readNBytes(maxBytes);
            if (maxBytes >= 0 && bytes.length == maxBytes && stream.read() != -1) {
                throw overLimit(maxBytes);
            }
            return new BufferedBody(bytes, contentType());
        }
    }

    private static IOException overLimit(int limit) {
        return new IOException("HTTP body exceeds the buffer limit of " + limit + " bytes");
    }

    /** Reads the entire body into memory. It must fit in one byte array. */
    default FullyBuffered buffer() throws IOException {
        return buffer(-1);
    }

    /** A repeatable body with a known length and a fresh, read-only view of its bytes. */
    interface FullyBuffered extends HttpBody {
        ByteBuffer bytes();

        @Override
        default boolean repeatable() {
            return true;
        }

        @Override
        default OptionalLong contentLength() {
            return OptionalLong.of(bytes().remaining());
        }
    }

    static FullyBuffered empty() {
        return BufferedBody.EMPTY;
    }

    static FullyBuffered ofBytes(byte[] bytes, String contentType) {
        return new BufferedBody(bytes.clone(), Optional.ofNullable(contentType));
    }

    static HttpBody ofStream(IOSupplier<InputStream> source, OptionalLong length,
            Optional<String> contentType, boolean repeatable) {
        return new StreamBody(Objects.requireNonNull(source), Objects.requireNonNull(length),
                Objects.requireNonNull(contentType), repeatable);
    }
}

record StreamBody(IOSupplier<InputStream> source, OptionalLong contentLength,
        Optional<String> contentType, boolean repeatable) implements HttpBody {
    @Override
    public InputStream openStream() throws IOException {
        return source.get();
    }
}

final class BufferedBody implements HttpBody.FullyBuffered {
    static final BufferedBody EMPTY = new BufferedBody(new byte[0], Optional.empty());
    private final byte[] data;
    private final Optional<String> type;

    BufferedBody(byte[] data, Optional<String> type) {
        this.data = data;
        this.type = type;
    }

    @Override
    public ByteBuffer bytes() {
        return ByteBuffer.wrap(data).asReadOnlyBuffer();
    }

    @Override
    public Optional<String> contentType() {
        return type;
    }

    @Override
    public InputStream openStream() {
        return new ByteArrayInputStream(data);
    }
}
