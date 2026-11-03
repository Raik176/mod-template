package de.rhm176.build.model.mapping;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

final class Http {
    private Http() {}

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    interface StreamReader<T> {
        T read(InputStream in) throws IOException;
    }

    private static final Pattern CONTENT_RANGE = Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)");

    record Slice(long start, long total, byte[] bytes) {}

    private static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", "rhm176-mod-build");
    }

    static <T> T stream(String url, StreamReader<T> reader) throws IOException {
        HttpRequest request = request(url)
                .header("Accept-Encoding", "gzip")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IOException("GET " + url + " returned HTTP " + response.statusCode());
            }
            try (InputStream in = decoded(response)) {
                return reader.read(in);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading " + url, e);
        }
    }

    static Slice range(String url, String range) throws IOException {
        HttpRequest request = request(url)
                .header("Accept-Encoding", "identity")
                .header("Range", "bytes=" + range)
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                if (response.statusCode() == 200) return null;
                if (response.statusCode() != 206) {
                    throw new IOException("GET " + url + " returned HTTP " + response.statusCode());
                }
                String encoding = response.headers().firstValue("Content-Encoding").orElse("identity");
                Matcher m = CONTENT_RANGE.matcher(response.headers().firstValue("Content-Range").orElse(""));
                if (!encoding.equalsIgnoreCase("identity") || !m.matches()) return null;
                long start = Long.parseLong(m.group(1));
                long end = Long.parseLong(m.group(2));
                byte[] bytes = in.readAllBytes();
                if (bytes.length != end - start + 1) throw new IOException("GET " + url + " returned a truncated range");
                return new Slice(start, Long.parseLong(m.group(3)), bytes);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading " + url, e);
        }
    }

    private static InputStream decoded(HttpResponse<InputStream> response) throws IOException {
        InputStream body = response.body();
        String encoding = response.headers().firstValue("Content-Encoding").orElse("");
        if (!encoding.equalsIgnoreCase("gzip") && !encoding.equalsIgnoreCase("x-gzip")) return body;
        try {
            return new GZIPInputStream(body, 1 << 16);
        } catch (IOException e) {
            body.close();
            throw e;
        }
    }

    static String text(String url) throws IOException {
        return stream(url, in -> new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }

    static byte[] bytes(String url) throws IOException {
        return stream(url, InputStream::readAllBytes);
    }
}
