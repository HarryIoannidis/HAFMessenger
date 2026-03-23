package com.haf.client.controllers;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainContentLoaderTest {

    @Test
    void refresh_future_if_failed_creates_new_future_when_not_loading() {
        CompletableFuture<String> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("load failed"));

        CompletableFuture<String> refreshed = MainContentLoader.refreshFutureIfFailed(failed, false);

        assertNotSame(failed, refreshed);
        assertTrue(!refreshed.isDone());
    }

    @Test
    void refresh_future_if_failed_keeps_existing_future_while_loading() {
        CompletableFuture<String> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("load failed"));

        CompletableFuture<String> refreshed = MainContentLoader.refreshFutureIfFailed(failed, true);

        assertSame(failed, refreshed);
    }

    @Test
    void refresh_future_if_failed_keeps_successful_future() {
        CompletableFuture<String> successful = CompletableFuture.completedFuture("ok");

        CompletableFuture<String> refreshed = MainContentLoader.refreshFutureIfFailed(successful, false);

        assertSame(successful, refreshed);
    }
}
