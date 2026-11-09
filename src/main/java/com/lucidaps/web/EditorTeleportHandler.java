/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 */
package com.lucidaps.web;

import com.google.gson.Gson;
import com.lucidaps.commands.PcTpCommand;
import com.lucidaps.db.DatabaseMariadb;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

final class EditorTeleportHandler implements HttpHandler {
    private final Predicate<String> authorized;
    private final BooleanSupplier connected;
    private final Lookup lookup;
    private final Issuer issuer;
    private final Consumer<Throwable> reportFailure;
    private final long timeoutMillis;
    private final Gson gson = new Gson();

    EditorTeleportHandler(Predicate<String> predicate, BooleanSupplier booleanSupplier, Lookup lookup, Issuer issuer, Consumer<Throwable> consumer) {
        this(predicate, booleanSupplier, lookup, issuer, consumer, 5000L);
    }

    EditorTeleportHandler(Predicate<String> predicate, BooleanSupplier booleanSupplier, Lookup lookup, Issuer issuer, Consumer<Throwable> consumer, long timeoutMillis) {
        this.authorized = predicate;
        this.connected = booleanSupplier;
        this.lookup = lookup;
        this.issuer = issuer;
        this.reportFailure = consumer;
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        httpExchange.getResponseHeaders().set("Cache-Control", "no-store");
        if (!"POST".equalsIgnoreCase(httpExchange.getRequestMethod())) {
            httpExchange.getResponseHeaders().set("Allow", "POST");
            httpExchange.sendResponseHeaders(405, -1L);
            httpExchange.close();
            return;
        }

        Map<String, String> map = EditorWebServer.queryToMap(httpExchange.getRequestURI().getRawQuery());
        if (!this.authorized.test(map.get("token"))) {
            httpExchange.sendResponseHeaders(403, -1L);
            httpExchange.close();
            return;
        }

        int id;
        try {
            id = Integer.parseInt(map.getOrDefault("id", ""));
            if (id <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException numberFormatException) {
            httpExchange.sendResponseHeaders(400, -1L);
            httpExchange.close();
            return;
        }

        if (!this.connected.getAsBoolean()) {
            EditorTeleportHandler.unavailable(httpExchange);
            httpExchange.close();
            return;
        }

        CompletableFuture<PcTpCommand.IssuedCommand> completableFuture = null;
        try {
            DatabaseMariadb.EditorTeleportLocation editorTeleportLocation = this.lookup.find(id);
            if (editorTeleportLocation == null) {
                httpExchange.sendResponseHeaders(404, -1L);
                return;
            }

            completableFuture = this.issuer.issue(editorTeleportLocation);
            PcTpCommand.IssuedCommand issuedCommand = completableFuture.get(this.timeoutMillis, TimeUnit.MILLISECONDS);
            if (issuedCommand == null) {
                httpExchange.sendResponseHeaders(404, -1L);
                return;
            }

            if (!this.authorized.test(map.get("token"))) {
                httpExchange.sendResponseHeaders(403, -1L);
                return;
            }

            byte[] responseBytes = this.gson.toJson(issuedCommand).getBytes(StandardCharsets.UTF_8);
            httpExchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            httpExchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream outputStream = httpExchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } catch (SQLException sQLException) {
            this.reportFailure.accept(sQLException);
            int status = this.connected.getAsBoolean() ? EditorWebServer.databaseFailureStatus(sQLException) : 503;
            if (status == 503) {
                EditorTeleportHandler.unavailable(httpExchange);
            } else {
                httpExchange.sendResponseHeaders(status, -1L);
            }
        } catch (ExecutionException exception) {
            if (completableFuture != null) {
                completableFuture.cancel(false);
            }
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            this.reportFailure.accept(cause);
            EditorTeleportHandler.unavailable(httpExchange);
        } catch (TimeoutException exception) {
            if (completableFuture != null) {
                completableFuture.cancel(false);
            }
            this.reportFailure.accept(exception);
            EditorTeleportHandler.unavailable(httpExchange);
        } catch (InterruptedException interruptedException) {
            if (completableFuture != null) {
                completableFuture.cancel(false);
            }
            Thread.currentThread().interrupt();
            EditorTeleportHandler.unavailable(httpExchange);
        } catch (Throwable throwable) {
            if (completableFuture != null) {
                completableFuture.cancel(false);
            }
            this.reportFailure.accept(throwable);
            EditorTeleportHandler.unavailable(httpExchange);
        } finally {
            httpExchange.close();
        }
    }

    private static void unavailable(HttpExchange httpExchange) {
        try {
            httpExchange.getResponseHeaders().set("Retry-After", "10");
            httpExchange.sendResponseHeaders(503, -1L);
        } catch (IOException ignored) {
        }
    }

    static interface Lookup {
        public DatabaseMariadb.EditorTeleportLocation find(int var1) throws SQLException;
    }

    static interface Issuer {
        public CompletableFuture<PcTpCommand.IssuedCommand> issue(DatabaseMariadb.EditorTeleportLocation var1);
    }
}
