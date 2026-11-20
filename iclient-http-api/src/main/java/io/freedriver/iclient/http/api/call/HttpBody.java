package io.freedriver.iclient.http.api.call;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** A stream source. The caller closes each stream and opens a one-shot body only once. */
public interface HttpBody {
    InputStream openStream() throws IOException;
    boolean repeatable();
    OptionalLong contentLength();
    Optional<String> contentType();

    static HttpBody empty() {
        return StreamBody.EMPTY;
    }

    static HttpBody ofBytes(byte[] bytes, String contentType) {
        byte[] copy = bytes.clone();
        return ofStream(() -> new ByteArrayInputStream(copy), OptionalLong.of(copy.length),
                Optional.ofNullable(contentType), true);
    }

    static HttpBody ofStream(IOSupplier<InputStream> source, OptionalLong length,
            Optional<String> contentType, boolean repeatable) {
        return new StreamBody(Objects.requireNonNull(source), Objects.requireNonNull(length),
                Objects.requireNonNull(contentType), repeatable);
    }
}

record StreamBody(IOSupplier<InputStream> source, OptionalLong contentLength,
        Optional<String> contentType, boolean repeatable) implements HttpBody {
    static final HttpBody EMPTY = HttpBody.ofBytes(new byte[0], null);

    @Override
    public InputStream openStream() throws IOException {
        return source.get();
    }
}
