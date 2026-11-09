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
import com.lucidaps.web.EditorWebServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
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

final class EditorTeleportHandler
implements HttpHandler {
    private final Predicate<String> authorized;
    private final BooleanSupplier connected;
    private final Lookup lookup;
    private final Issuer issuer;
    private final Consumer<Exception> reportFailure;
    private final long timeoutMillis;
    private final Gson gson = new Gson();

    EditorTeleportHandler(Predicate<String> predicate, BooleanSupplier booleanSupplier, Lookup lookup, Issuer issuer, Consumer<Exception> consumer) {
        this(predicate, booleanSupplier, lookup, issuer, consumer, 2000L);
    }

    EditorTeleportHandler(Predicate<String> predicate, BooleanSupplier booleanSupplier, Lookup lookup, Issuer issuer, Consumer<Exception> consumer, long l) {
        this.authorized = predicate;
        this.connected = booleanSupplier;
        this.lookup = lookup;
        this.issuer = issuer;
        this.reportFailure = consumer;
        this.timeoutMillis = l;
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    @Override
    public void handle(HttpExchange httpExchange) {
        int n;
        Map<String, String> map;
        HttpExchange httpExchange2;
        block34: {
            httpExchange2 = httpExchange;
            httpExchange.getResponseHeaders().set("Cache-Control", "no-store");
            if (!"POST".equalsIgnoreCase(httpExchange.getRequestMethod())) {
                httpExchange.getResponseHeaders().set("Allow", "POST");
                httpExchange.sendResponseHeaders(405, -1L);
                if (httpExchange2 == null) return;
                httpExchange2.close();
                return;
            }
            map = EditorWebServer.queryToMap(httpExchange.getRequestURI().getRawQuery());
            if (!this.authorized.test(map.get("token"))) {
                httpExchange.sendResponseHeaders(403, -1L);
                if (httpExchange2 == null) return;
                httpExchange2.close();
                return;
            }
            try {
                n = Integer.parseInt(map.getOrDefault("id", ""));
                if (n <= 0) {
                    throw new NumberFormatException();
                }
            }
            catch (NumberFormatException numberFormatException) {
                httpExchange.sendResponseHeaders(400, -1L);
                if (httpExchange2 == null) return;
                httpExchange2.close();
                return;
            }
            if (this.connected.getAsBoolean()) break block34;
            EditorTeleportHandler.unavailable(httpExchange);
            if (httpExchange2 == null) return;
            httpExchange2.close();
            return;
        }
        try {
            PcTpCommand.IssuedCommand issuedCommand;
            CompletableFuture<PcTpCommand.IssuedCommand> completableFuture;
            block37: {
                block36: {
                    DatabaseMariadb.EditorTeleportLocation editorTeleportLocation;
                    block35: {
                        completableFuture = null;
                        editorTeleportLocation = this.lookup.find(n);
                        if (editorTeleportLocation != null) break block35;
                        httpExchange.sendResponseHeaders(404, -1L);
                        if (httpExchange2 == null) return;
                        httpExchange2.close();
                        return;
                    }
                    completableFuture = this.issuer.issue(editorTeleportLocation);
                    issuedCommand = completableFuture.get(this.timeoutMillis, TimeUnit.MILLISECONDS);
                    if (issuedCommand != null) break block36;
                    httpExchange.sendResponseHeaders(404, -1L);
                    if (httpExchange2 == null) return;
                    httpExchange2.close();
                    return;
                }
                if (this.authorized.test(map.get("token"))) break block37;
                httpExchange.sendResponseHeaders(403, -1L);
                if (httpExchange2 == null) return;
                httpExchange2.close();
                return;
            }
            try {
                byte[] byArray = this.gson.toJson((Object)issuedCommand).getBytes(StandardCharsets.UTF_8);
                httpExchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                httpExchange.sendResponseHeaders(200, byArray.length);
                try (OutputStream outputStream = httpExchange.getResponseBody();){
                    outputStream.write(byArray);
                    return;
                }
            }
            catch (SQLException sQLException) {
                int n2;
                this.reportFailure.accept(sQLException);
                int n3 = n2 = this.connected.getAsBoolean() ? EditorWebServer.databaseFailureStatus(sQLException) : 503;
                if (n2 == 503) {
                    EditorTeleportHandler.unavailable(httpExchange);
                    return;
                }
                httpExchange.sendResponseHeaders(n2, -1L);
                return;
            }
            catch (ExecutionException | TimeoutException exception) {
                if (completableFuture != null) {
                    completableFuture.cancel(false);
                }
                this.reportFailure.accept(exception);
                EditorTeleportHandler.unavailable(httpExchange);
                return;
            }
            catch (InterruptedException interruptedException) {
                if (completableFuture != null) {
                    completableFuture.cancel(false);
                }
                Thread.currentThread().interrupt();
                EditorTeleportHandler.unavailable(httpExchange);
                return;
            }
            catch (RuntimeException runtimeException) {
                if (completableFuture != null) {
                    completableFuture.cancel(false);
                }
                this.reportFailure.accept(runtimeException);
                EditorTeleportHandler.unavailable(httpExchange);
                return;
            }
        }
        finally {
            if (httpExchange2 != null) {
                httpExchange2.close();
            }
        }
    }

    private static void unavailable(HttpExchange httpExchange) {
        httpExchange.getResponseHeaders().set("Retry-After", "10");
        httpExchange.sendResponseHeaders(503, -1L);
    }

    static interface Lookup {
        public DatabaseMariadb.EditorTeleportLocation find(int var1) throws SQLException;
    }

    static interface Issuer {
        public CompletableFuture<PcTpCommand.IssuedCommand> issue(DatabaseMariadb.EditorTeleportLocation var1);
    }
}

