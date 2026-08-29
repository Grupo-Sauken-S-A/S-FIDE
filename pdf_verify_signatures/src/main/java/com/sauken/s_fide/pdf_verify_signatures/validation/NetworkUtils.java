package com.sauken.s_fide.pdf_verify_signatures.validation;

import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.logging.Logger;

public class NetworkUtils {
    private static final Logger LOGGER = Logger.getLogger(NetworkUtils.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    public static boolean isInternetAvailable() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://8.8.8.8"))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            client.send(request, HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (IOException | InterruptedException e) {
            LOGGER.fine("Internet no disponible: " + e.getMessage());
            return false;
        }
    }

    public static HttpResponse<byte[]> sendRequest(String url, byte[] data, String contentType)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT);

        if (data != null) {
            requestBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(data))
                    .header("Content-Type", contentType);
        } else {
            requestBuilder.GET();
        }

        return client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}