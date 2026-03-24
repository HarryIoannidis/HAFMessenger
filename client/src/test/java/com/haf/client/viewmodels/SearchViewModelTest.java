package com.haf.client.viewmodels;

import com.haf.client.utils.RuntimeIssue;
import com.haf.client.utils.UiConstants;
import com.haf.shared.responses.UserSearchResponse;
import com.haf.shared.dto.UserSearchResultDTO;
import com.haf.shared.utils.JsonCodec;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class SearchViewModelTest {

    @Test
    void search_blank_query_clears_state_without_network_call() {
        AtomicBoolean called = new AtomicBoolean(false);
        SearchViewModel viewModel = new SearchViewModel((query, limit, cursor) -> {
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
    void search_short_query_does_not_call_network() {
        AtomicBoolean called = new AtomicBoolean(false);
        SearchViewModel viewModel = new SearchViewModel((query, limit, cursor) -> {
            called.set(true);
            return "{}";
        });

        viewModel.search("ab");

        assertFalse(called.get());
        assertEquals(SearchViewModel.STATUS_MIN_QUERY, viewModel.statusTextProperty().get());
    }

    @Test
    void search_success_updates_results() {
        AtomicReference<String> receivedQuery = new AtomicReference<>();
        AtomicReference<Integer> receivedLimit = new AtomicReference<>();
        AtomicReference<String> receivedCursor = new AtomicReference<>();
        UserSearchResponse response = UserSearchResponse.success(List.of(
                new UserSearchResultDTO("u-1", "Jane Doe", "123", "jane@haf.gr", "SMINIAS", true)));

        SearchViewModel viewModel = new SearchViewModel((query, limit, cursor) -> {
            receivedQuery.set(query);
            receivedLimit.set(limit);
            receivedCursor.set(cursor);
            return JsonCodec.toJson(response);
        });

        viewModel.search("Jane");

        awaitCondition(() -> !viewModel.loadingProperty().get() && viewModel.hasResultsProperty().get());

        assertEquals("Jane", receivedQuery.get());
        assertEquals(20, receivedLimit.get());
        assertNull(receivedCursor.get());
        assertEquals(1, viewModel.resultsProperty().size());
        assertEquals("u-1", viewModel.resultsProperty().getFirst().getUserId());
        assertEquals("", viewModel.statusTextProperty().get());
    }

    @Test
    void loadMore_appends_next_page() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> secondCursor = new AtomicReference<>();
        UserSearchResponse first = UserSearchResponse.success(List.of(
                new UserSearchResultDTO("u-1", "Jane Doe", "123", "jane@haf.gr", "SMINIAS", true)),
                true, "cursor-1");
        UserSearchResponse second = UserSearchResponse.success(List.of(
                new UserSearchResultDTO("u-2", "John Doe", "124", "john@haf.gr", "SMINIAS", true)),
                false, null);

        SearchViewModel viewModel = new SearchViewModel((query, limit, cursor) -> {
            if (calls.getAndIncrement() == 0) {
                return JsonCodec.toJson(first);
            }
            secondCursor.set(cursor);
            return JsonCodec.toJson(second);
        });

        viewModel.search("Jane");
        awaitCondition(() -> viewModel.resultsProperty().size() == 1);

        viewModel.loadMore();
        awaitCondition(() -> viewModel.resultsProperty().size() == 2);

        assertEquals("cursor-1", secondCursor.get());
        assertEquals("u-2", viewModel.resultsProperty().get(1).getUserId());
    }

    @Test
    void sort_by_full_name_supports_ascending_and_descending() {
        UserSearchResponse response = UserSearchResponse.success(List.of(
                new UserSearchResultDTO("u-1", "Zoe", "10", "zoe@haf.gr", UiConstants.RANK_SMINIAS, true),
                new UserSearchResultDTO("u-2", "anna", "11", "anna@haf.gr", UiConstants.RANK_SMINIAS, true),
                new UserSearchResultDTO("u-3", "Mike", "12", "mike@haf.gr", UiConstants.RANK_SMINIAS, true)));

        SearchViewModel viewModel = new SearchViewModel((query, limit, cursor) -> JsonCodec.toJson(response));
        viewModel.search("name", new SearchSortViewModel.SortOptions(SearchSortViewModel.Field.FULL_NAME, SearchSortViewModel.Direction.ASC));
        awaitCondition(() -> matchesOrder(viewModel, List.of("u-2", "u-3", "u-1")));
        assertEquals(List.of("u-2", "u-3", "u-1"), toUserIds(viewModel));

        viewModel.search("name", new SearchSortViewModel.SortOptions(SearchSortViewModel.Field.FULL_NAME, SearchSortViewModel.Direction.DESC));
        awaitCondition(() -> matchesOrder(viewModel, List.of("u-1", "u-3", "u-2")));
        assertEquals(List.of("u-1", "u-3", "u-2"), toUserIds(viewModel));
    }

    @Test
    void sort_by_reg_number_uses_numeric_ordering() {
        UserSearchResponse response = UserSearchResponse.success(List.of(
                new UserSearchResultDTO("u-10", "Ten", "10", "ten@haf.gr", UiConstants.RANK_SMINIAS, true),
                new UserSearchResultDTO("u-2", "Two", "2", "two@haf.gr", UiConstants.RANK_SMINIAS, true),
                new UserSearchResultDTO("u-1", "One", "1", "one@haf.gr", UiConstants.RANK_SMINIAS, true)));

        SearchViewModel viewModel = new SearchViewModel((query, limit, cursor) -> JsonCodec.toJson(response));
        viewModel.search("regg", new SearchSortViewModel.SortOptions(SearchSortViewModel.Field.REG_NUMBER, SearchSortViewModel.Direction.ASC));
        awaitCondition(() -> matchesOrder(viewModel, List.of("u-1", "u-2", "u-10")));
        assertEquals(List.of("u-1", "u-2", "u-10"), toUserIds(viewModel));

        viewModel.search("regg", new SearchSortViewModel.SortOptions(SearchSortViewModel.Field.REG_NUMBER, SearchSortViewModel.Direction.DESC));
        awaitCondition(() -> matchesOrder(viewModel, List.of("u-10", "u-2", "u-1")));
        assertEquals(List.of("u-10", "u-2", "u-1"), toUserIds(viewModel));
    }

    @Test
    void sort_by_rank_follows_hierarchy_including_pterarchos() {
        UserSearchResponse response = UserSearchResponse.success(List.of(
                new UserSearchResultDTO("u-high", "High", "100", "high@haf.gr", UiConstants.RANK_PTERARCHOS, true),
                new UserSearchResultDTO("u-mid", "Mid", "101", "mid@haf.gr", UiConstants.RANK_SMINAGOS, true),
                new UserSearchResultDTO("u-low", "Low", "102", "low@haf.gr", UiConstants.RANK_YPOSMINIAS, true)));

        SearchViewModel viewModel = new SearchViewModel((query, limit, cursor) -> JsonCodec.toJson(response));
        viewModel.search("rank", new SearchSortViewModel.SortOptions(SearchSortViewModel.Field.RANK, SearchSortViewModel.Direction.ASC));
        awaitCondition(() -> matchesOrder(viewModel, List.of("u-low", "u-mid", "u-high")));
        assertEquals(List.of("u-low", "u-mid", "u-high"), toUserIds(viewModel));

        viewModel.search("rank", new SearchSortViewModel.SortOptions(SearchSortViewModel.Field.RANK, SearchSortViewModel.Direction.DESC));
        awaitCondition(() -> matchesOrder(viewModel, List.of("u-high", "u-mid", "u-low")));
        assertEquals(List.of("u-high", "u-mid", "u-low"), toUserIds(viewModel));
    }

    @Test
    void load_more_keeps_results_sorted_after_append() {
        AtomicInteger calls = new AtomicInteger();
        UserSearchResponse first = UserSearchResponse.success(List.of(
                new UserSearchResultDTO("u-30", "Thirty", "30", "thirty@haf.gr", UiConstants.RANK_SMINIAS, true),
                new UserSearchResultDTO("u-10", "Ten", "10", "ten@haf.gr", UiConstants.RANK_SMINIAS, true)),
                true, "cursor-1");
        UserSearchResponse second = UserSearchResponse.success(List.of(
                new UserSearchResultDTO("u-20", "Twenty", "20", "twenty@haf.gr", UiConstants.RANK_SMINIAS, true)),
                false, null);

        SearchViewModel viewModel = new SearchViewModel((query, limit, cursor) -> {
            if (calls.getAndIncrement() == 0) {
                return JsonCodec.toJson(first);
            }
            return JsonCodec.toJson(second);
        });

        viewModel.search("more", new SearchSortViewModel.SortOptions(SearchSortViewModel.Field.REG_NUMBER, SearchSortViewModel.Direction.ASC));
        awaitCondition(() -> matchesOrder(viewModel, List.of("u-10", "u-30")));
        assertEquals(List.of("u-10", "u-30"), toUserIds(viewModel));

        viewModel.loadMore();
        awaitCondition(() -> matchesOrder(viewModel, List.of("u-10", "u-20", "u-30")));
        assertEquals(List.of("u-10", "u-20", "u-30"), toUserIds(viewModel));
    }

    @Test
    void search_no_results_sets_no_results_status() {
        UserSearchResponse response = UserSearchResponse.success(List.of());
        SearchViewModel viewModel = new SearchViewModel((query, limit, cursor) -> JsonCodec.toJson(response));

        viewModel.search("nobody");

        awaitCondition(() -> !viewModel.loadingProperty().get()
                && SearchViewModel.STATUS_NO_RESULTS.equals(viewModel.statusTextProperty().get()));

        assertFalse(viewModel.hasResultsProperty().get());
        assertTrue(viewModel.resultsProperty().isEmpty());
    }

    @Test
    void search_error_response_sets_error_status() {
        UserSearchResponse response = UserSearchResponse.error("Server unavailable");
        SearchViewModel viewModel = new SearchViewModel((query, limit, cursor) -> JsonCodec.toJson(response));

        viewModel.search("query");

        awaitCondition(() -> !viewModel.loadingProperty().get()
                && "Error: Server unavailable".equals(viewModel.statusTextProperty().get()));

        assertFalse(viewModel.hasResultsProperty().get());
        assertTrue(viewModel.resultsProperty().isEmpty());
    }

    @Test
    void search_exception_sets_generic_failure_status() {
        SearchViewModel viewModel = new SearchViewModel((query, limit, cursor) -> {
            throw new RuntimeException("boom");
        });

        viewModel.search("query");

        awaitCondition(() -> !viewModel.loadingProperty().get()
                && SearchViewModel.STATUS_GENERIC_FAILURE.equals(viewModel.statusTextProperty().get()));

        assertFalse(viewModel.hasResultsProperty().get());
        assertTrue(viewModel.resultsProperty().isEmpty());
    }

    @Test
    void search_exception_emits_runtime_issue_and_retry_reexecutes_query() {
        AtomicInteger calls = new AtomicInteger();
        UserSearchResponse success = UserSearchResponse.success(List.of(
                new UserSearchResultDTO("u-1", "Jane Doe", "123", "jane@haf.gr", "SMINIAS", true)));
        SearchViewModel viewModel = new SearchViewModel((query, limit, cursor) -> {
            if (calls.getAndIncrement() == 0) {
                throw new RuntimeException("boom");
            }
            return JsonCodec.toJson(success);
        });
        List<RuntimeIssue> issues = new CopyOnWriteArrayList<>();
        viewModel.addRuntimeIssueListener(issues::add);

        viewModel.search("query");
        awaitCondition(() -> !issues.isEmpty());

        assertEquals("search.request.failed", issues.getFirst().dedupeKey());
        issues.getFirst().retryAction().run();

        awaitCondition(() -> viewModel.resultsProperty().size() == 1);
        assertEquals(2, calls.get());
    }

    @Test
    void search_error_response_emits_runtime_issue_and_retry_reexecutes_query() {
        AtomicInteger calls = new AtomicInteger();
        UserSearchResponse failure = UserSearchResponse.error("Server unavailable");
        UserSearchResponse success = UserSearchResponse.success(List.of(
                new UserSearchResultDTO("u-2", "John Doe", "124", "john@haf.gr", "SMINIAS", true)));
        SearchViewModel viewModel = new SearchViewModel((query, limit, cursor) -> {
            if (calls.getAndIncrement() == 0) {
                return JsonCodec.toJson(failure);
            }
            return JsonCodec.toJson(success);
        });
        List<RuntimeIssue> issues = new CopyOnWriteArrayList<>();
        viewModel.addRuntimeIssueListener(issues::add);

        viewModel.search("query");
        awaitCondition(() -> !issues.isEmpty());

        assertEquals("search.response.error", issues.getFirst().dedupeKey());
        issues.getFirst().retryAction().run();

        awaitCondition(() -> viewModel.resultsProperty().size() == 1);
        assertEquals(2, calls.get());
    }

    private static List<String> toUserIds(SearchViewModel viewModel) {
        List<UserSearchResultDTO> snapshot = new ArrayList<>(viewModel.resultsProperty());
        List<String> userIds = new ArrayList<>(snapshot.size());
        for (UserSearchResultDTO dto : snapshot) {
            userIds.add(dto.getUserId());
        }
        return userIds;
    }

    private static boolean matchesOrder(SearchViewModel viewModel, List<String> expectedUserIds) {
        try {
            return expectedUserIds.equals(toUserIds(viewModel));
        } catch (RuntimeException ex) {
            return false;
        }
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
