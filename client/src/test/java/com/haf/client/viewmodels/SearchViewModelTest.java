package com.haf.client.viewmodels;

import com.haf.shared.dto.UserSearchResponse;
import com.haf.shared.dto.UserSearchResult;
import com.haf.shared.utils.JsonCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class SearchViewModelTest {

    @Test
    void search_blank_query_clears_state_without_network_call() {
        AtomicBoolean called = new AtomicBoolean(false);
        SearchViewModel viewModel = new SearchViewModel(query -> {
            called.set(true);
            return "{}";
        });

        viewModel.search("   ");

        assertFalse(called.get());
        assertFalse(viewModel.loadingProperty().get());
        assertFalse(viewModel.hasResultsProperty().get());
        assertTrue(viewModel.resultsProperty().isEmpty());
        assertEquals(SearchViewModel.STATUS_IDLE, viewModel.statusTextProperty().get());
    }

    @Test
    void search_success_updates_results() {
        AtomicReference<String> receivedQuery = new AtomicReference<>();
        UserSearchResponse response = UserSearchResponse.success(List.of(
                new UserSearchResult("u-1", "Jane Doe", "123", "jane@haf.gr", "SMINIAS", true)));

        SearchViewModel viewModel = new SearchViewModel(query -> {
            receivedQuery.set(query);
            return JsonCodec.toJson(response);
        });

        viewModel.search("Jane");

        awaitCondition(() -> !viewModel.loadingProperty().get() && viewModel.hasResultsProperty().get());

        assertEquals("Jane", receivedQuery.get());
        assertEquals(1, viewModel.resultsProperty().size());
        assertEquals("u-1", viewModel.resultsProperty().getFirst().getUserId());
        assertEquals("", viewModel.statusTextProperty().get());
    }

    @Test
    void search_no_results_sets_no_results_status() {
        UserSearchResponse response = UserSearchResponse.success(List.of());
        SearchViewModel viewModel = new SearchViewModel(query -> JsonCodec.toJson(response));

        viewModel.search("nobody");

        awaitCondition(() -> !viewModel.loadingProperty().get()
                && SearchViewModel.STATUS_NO_RESULTS.equals(viewModel.statusTextProperty().get()));

        assertFalse(viewModel.hasResultsProperty().get());
        assertTrue(viewModel.resultsProperty().isEmpty());
    }

    @Test
    void search_error_response_sets_error_status() {
        UserSearchResponse response = UserSearchResponse.error("Server unavailable");
        SearchViewModel viewModel = new SearchViewModel(query -> JsonCodec.toJson(response));

        viewModel.search("query");

        awaitCondition(() -> !viewModel.loadingProperty().get()
                && "Error: Server unavailable".equals(viewModel.statusTextProperty().get()));

        assertFalse(viewModel.hasResultsProperty().get());
        assertTrue(viewModel.resultsProperty().isEmpty());
    }

    @Test
    void search_exception_sets_generic_failure_status() {
        SearchViewModel viewModel = new SearchViewModel(query -> {
            throw new RuntimeException("boom");
        });

        viewModel.search("query");

        awaitCondition(() -> !viewModel.loadingProperty().get()
                && SearchViewModel.STATUS_GENERIC_FAILURE.equals(viewModel.statusTextProperty().get()));

        assertFalse(viewModel.hasResultsProperty().get());
        assertTrue(viewModel.resultsProperty().isEmpty());
    }

    private static void awaitCondition(BooleanSupplier condition) {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadlineNanos) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for async search completion");
            }
        }
        fail("Timed out waiting for async search completion");
    }
}
