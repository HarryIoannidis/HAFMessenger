package com.haf.client.controllers;

import com.haf.client.viewmodels.SearchSortViewModel;
import javafx.scene.Node;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchFilterUiTest {

    @Test
    void first_search_trigger_opens_popup_without_executing_search() {
        StubPopup popup = new StubPopup();
        StubSearchExecutor searchExecutor = new StubSearchExecutor();
        SearchFilterController.FlowController flow = new SearchFilterController.FlowController(popup, searchExecutor, () -> {
        });

        flow.onSearchTrigger("john", null);

        assertEquals(1, popup.showCalls.get());
        assertEquals(0, searchExecutor.calls.get());
        assertTrue(flow.isApplyRequiredBeforeSearch());
    }

    @Test
    void apply_executes_search_and_disables_first_search_gate() {
        StubPopup popup = new StubPopup();
        StubSearchExecutor searchExecutor = new StubSearchExecutor();
        AtomicInteger executedCallbacks = new AtomicInteger();
        SearchFilterController.FlowController flow = new SearchFilterController.FlowController(
                popup,
                searchExecutor,
                executedCallbacks::incrementAndGet);

        flow.onSearchTrigger("john", null);
        SearchSortViewModel.SortOptions selected = new SearchSortViewModel.SortOptions(
                SearchSortViewModel.Field.RANK,
                SearchSortViewModel.Direction.DESC);
        popup.apply(selected);

        assertEquals(1, searchExecutor.calls.get());
        assertEquals("john", searchExecutor.lastQuery.get());
        assertEquals(selected, searchExecutor.lastOptions.get());
        assertEquals(1, executedCallbacks.get());
        assertFalse(flow.isApplyRequiredBeforeSearch());
    }

    @Test
    void subsequent_search_runs_immediately_after_first_apply() {
        StubPopup popup = new StubPopup();
        StubSearchExecutor searchExecutor = new StubSearchExecutor();
        SearchFilterController.FlowController flow = new SearchFilterController.FlowController(popup, searchExecutor, () -> {
        });

        flow.onSearchTrigger("john", null);
        popup.apply(SearchSortViewModel.SortOptions.DEFAULT);
        flow.onSearchTrigger("mike", null);

        assertEquals(1, popup.showCalls.get());
        assertEquals(2, searchExecutor.calls.get());
        assertEquals("mike", searchExecutor.lastQuery.get());
    }

    @Test
    void clear_reenables_gate_and_preserves_last_selected_sort() {
        StubPopup popup = new StubPopup();
        StubSearchExecutor searchExecutor = new StubSearchExecutor();
        SearchFilterController.FlowController flow = new SearchFilterController.FlowController(popup, searchExecutor, () -> {
        });

        SearchSortViewModel.SortOptions selected = new SearchSortViewModel.SortOptions(
                SearchSortViewModel.Field.REG_NUMBER,
                SearchSortViewModel.Direction.DESC);
        flow.onSearchTrigger("john", null);
        popup.apply(selected);

        flow.onClear();
        flow.onSearchTrigger("alex", null);

        assertTrue(flow.isApplyRequiredBeforeSearch());
        assertEquals(2, popup.showCalls.get());
        assertEquals(selected, popup.lastInitialOptions.get());
        assertEquals(1, searchExecutor.calls.get());
    }

    private static final class StubSearchExecutor implements SearchFilterController.SearchExecutor {
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<String> lastQuery = new AtomicReference<>();
        private final AtomicReference<SearchSortViewModel.SortOptions> lastOptions = new AtomicReference<>();

        @Override
        public boolean execute(String query, SearchSortViewModel.SortOptions sortOptions) {
            calls.incrementAndGet();
            lastQuery.set(query);
            lastOptions.set(sortOptions);
            return true;
        }
    }

    private static final class StubPopup implements SearchFilterController.PopupPort {
        private final AtomicInteger showCalls = new AtomicInteger();
        private final AtomicReference<SearchSortViewModel.SortOptions> lastInitialOptions = new AtomicReference<>();
        private Consumer<SearchSortViewModel.SortOptions> applyCallback;
        private boolean showing;

        @Override
        public void show(Node anchor, SearchSortViewModel.SortOptions initialOptions, boolean focusApply,
                Consumer<SearchSortViewModel.SortOptions> onApply) {
            showCalls.incrementAndGet();
            lastInitialOptions.set(initialOptions);
            applyCallback = onApply;
            showing = true;
        }

        @Override
        public void hide() {
            showing = false;
        }

        @Override
        public boolean isShowing() {
            return showing;
        }

        private void apply(SearchSortViewModel.SortOptions options) {
            if (applyCallback != null) {
                applyCallback.accept(options);
            }
        }
    }
}
