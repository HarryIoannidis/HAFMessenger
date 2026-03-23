package com.haf.client.controllers;

import com.haf.client.viewmodels.SearchSortViewModel;
import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Search-filter UI and interaction flow (popup + first-search apply gate).
 */
final class SearchFilterController {

    interface SearchExecutor {
        /**
         * Executes a search with the provided query and sort options.
         *
         * @param query query text to execute
         * @param sortOptions selected sort options
         * @return {@code true} when search execution succeeded and UI should react
         */
        boolean execute(String query, SearchSortViewModel.SortOptions sortOptions);
    }

    interface PopupPort {
        /**
         * Displays the filter popup anchored to a UI node.
         *
         * @param anchor anchor node for popup positioning
         * @param initialOptions options to pre-select in popup controls
         * @param focusApply whether the apply button should receive focus
         * @param onApply callback invoked when user applies selection
         */
        void show(Node anchor, SearchSortViewModel.SortOptions initialOptions, boolean focusApply,
                Consumer<SearchSortViewModel.SortOptions> onApply);

        /**
         * Hides the popup if visible.
         */
        void hide();

        /**
         * Indicates whether popup is currently visible.
         *
         * @return {@code true} when popup is visible
         */
        boolean isShowing();
    }

    static final class FlowController {

        private final PopupPort popup;
        private final SearchExecutor searchExecutor;
        private final Runnable onSearchExecuted;

        private boolean requireApplyBeforeSearch = true;
        private SearchSortViewModel.SortOptions selectedOptions = SearchSortViewModel.SortOptions.DEFAULT;
        private String pendingQuery = "";

        /**
         * Creates flow controller coordinating search triggers and filter popup
         * lifecycle.
         *
         * @param popup popup port used to show/hide filter UI
         * @param searchExecutor search executor callback
         * @param onSearchExecuted callback invoked when a search executes successfully
         */
        FlowController(PopupPort popup, SearchExecutor searchExecutor, Runnable onSearchExecuted) {
            this.popup = Objects.requireNonNull(popup, "popup");
            this.searchExecutor = Objects.requireNonNull(searchExecutor, "searchExecutor");
            this.onSearchExecuted = Objects.requireNonNull(onSearchExecuted, "onSearchExecuted");
        }

        /**
         * Resets first-search apply requirement when Search tab becomes active.
         */
        void onSearchTabActivated() {
            requireApplyBeforeSearch = true;
            pendingQuery = "";
            popup.hide();
        }

        /**
         * Handles search trigger action from query input or search button.
         *
         * @param query raw query text
         * @param anchor popup anchor node for first-search filter apply
         */
        void onSearchTrigger(String query, Node anchor) {
            String normalized = normalizeQuery(query);
            if (normalized.isBlank()) {
                return;
            }

            pendingQuery = normalized;
            if (requireApplyBeforeSearch) {
                openPopup(anchor, true);
                return;
            }

            executeSearch(normalized);
        }

        /**
         * Handles explicit filter-button trigger, toggling popup visibility.
         *
         * @param query current query text
         * @param anchor popup anchor node
         */
        void onFilterButtonTrigger(String query, Node anchor) {
            pendingQuery = normalizeQuery(query);
            if (popup.isShowing()) {
                popup.hide();
                return;
            }
            openPopup(anchor, true);
        }

        /**
         * Clears pending search state and resets apply-before-search behavior.
         */
        void onClear() {
            pendingQuery = "";
            requireApplyBeforeSearch = true;
            popup.hide();
        }

        /**
         * Returns currently selected sort options tracked by the flow controller.
         *
         * @return active sort options
         */
        SearchSortViewModel.SortOptions currentSortOptions() {
            return selectedOptions;
        }

        /**
         * Indicates whether the next search requires explicit filter apply.
         *
         * @return {@code true} when popup apply must happen before search execution
         */
        boolean isApplyRequiredBeforeSearch() {
            return requireApplyBeforeSearch;
        }

        /**
         * Opens the filter popup with current sort options.
         *
         * @param anchor popup anchor node
         * @param focusApply whether apply button should be focused
         */
        private void openPopup(Node anchor, boolean focusApply) {
            popup.show(anchor, selectedOptions, focusApply, this::applySelection);
        }

        /**
         * Applies selected sort options, clears first-search gate, and re-runs
         * pending query if present.
         *
         * @param options selected sort options
         */
        private void applySelection(SearchSortViewModel.SortOptions options) {
            if (options != null) {
                selectedOptions = options;
            }

            requireApplyBeforeSearch = false;
            popup.hide();
            if (!pendingQuery.isBlank()) {
                executeSearch(pendingQuery);
            }
        }

        /**
         * Executes search and emits post-search callback when successful.
         *
         * @param query normalized query text
         */
        private void executeSearch(String query) {
            if (searchExecutor.execute(query, selectedOptions)) {
                onSearchExecuted.run();
            }
        }

        /**
         * Normalizes query text for empty/whitespace-safe comparisons.
         *
         * @param query raw query text
         * @return trimmed query or empty string for null
         */
        private static String normalizeQuery(String query) {
            return query == null ? "" : query.trim();
        }
    }

    private final ContextMenu menu = new ContextMenu();
    private final ToggleGroup directionGroup = new ToggleGroup();
    private final ToggleGroup fieldGroup = new ToggleGroup();

    private final RadioButton ascendingRadio = new RadioButton("Ascending");
    private final RadioButton descendingRadio = new RadioButton("Descending");
    private final RadioButton fullNameRadio = new RadioButton("Full Name");
    private final RadioButton regNumberRadio = new RadioButton("Reg. number");
    private final RadioButton rankRadio = new RadioButton("Rank");
    private final JFXButton applyButton = new JFXButton("Apply");

    private Consumer<SearchSortViewModel.SortOptions> onApply = options -> {
    };
    private final FlowController flow;

    /**
     * Creates a search-filter controller with callbacks for search execution and
     * success notification.
     *
     * @param searchExecutor callback used to execute filtered search
     * @param onSearchExecuted callback invoked after successful search execution
     */
    SearchFilterController(SearchExecutor searchExecutor, Runnable onSearchExecuted) {
        this.flow = new FlowController(new PopupPort() {
            /**
             * Delegates popup show action to outer controller popup implementation.
             *
             * @param anchor popup anchor node
             * @param initialOptions options to preselect
             * @param focusApply whether apply button should be focused
             * @param onApply callback invoked on apply
             */
            @Override
            public void show(Node anchor, SearchSortViewModel.SortOptions initialOptions, boolean focusApply,
                    Consumer<SearchSortViewModel.SortOptions> onApply) {
                showPopupInternal(anchor, initialOptions, focusApply, onApply);
            }

            /**
             * Hides the filter menu.
             */
            @Override
            public void hide() {
                menu.hide();
            }

            /**
             * Indicates whether filter menu is visible.
             *
             * @return {@code true} when menu is currently shown
             */
            @Override
            public boolean isShowing() {
                return menu.isShowing();
            }
        }, searchExecutor, onSearchExecuted);

        menu.getStyleClass().add("dropdown-menu");
        menu.getStyleClass().add("search-filter-menu");
        menu.setAutoHide(true);

        CustomMenuItem contentItem = new CustomMenuItem(buildContent());
        contentItem.setHideOnClick(false);
        menu.getItems().add(contentItem);
    }

    /**
     * Forwards tab-activation event into flow controller.
     */
    void onSearchTabActivated() {
        flow.onSearchTabActivated();
    }

    /**
     * Forwards search trigger into flow controller.
     *
     * @param query current query text
     * @param anchor popup anchor node
     */
    void onSearchTrigger(String query, Node anchor) {
        flow.onSearchTrigger(query, anchor);
    }

    /**
     * Forwards filter-button trigger into flow controller.
     *
     * @param query current query text
     * @param anchor popup anchor node
     */
    void onFilterButtonTrigger(String query, Node anchor) {
        flow.onFilterButtonTrigger(query, anchor);
    }

    /**
     * Clears filter/search transient state through flow controller.
     */
    void onClear() {
        flow.onClear();
    }

    /**
     * Shows popup and initializes selected options + apply callback.
     *
     * @param anchor popup anchor node
     * @param initialOptions options to preselect
     * @param focusApply whether apply button should receive focus
     * @param onApply callback invoked when apply is pressed
     */
    private void showPopupInternal(Node anchor, SearchSortViewModel.SortOptions initialOptions, boolean focusApply,
            Consumer<SearchSortViewModel.SortOptions> onApply) {
        if (anchor == null) {
            return;
        }

        this.onApply = Objects.requireNonNullElse(onApply, options -> {
        });
        selectOptions(initialOptions);

        if (menu.isShowing()) {
            menu.hide();
        }
        menu.show(anchor, Side.BOTTOM, 0, 4);
        if (focusApply) {
            Platform.runLater(applyButton::requestFocus);
        }
    }

    /**
     * Builds popup content node tree and wires keyboard/apply behavior.
     *
     * @return configured popup content container
     */
    private VBox buildContent() {
        ascendingRadio.setToggleGroup(directionGroup);
        ascendingRadio.setUserData(SearchSortViewModel.Direction.ASC);
        descendingRadio.setToggleGroup(directionGroup);
        descendingRadio.setUserData(SearchSortViewModel.Direction.DESC);

        fullNameRadio.setToggleGroup(fieldGroup);
        fullNameRadio.setUserData(SearchSortViewModel.Field.FULL_NAME);
        regNumberRadio.setToggleGroup(fieldGroup);
        regNumberRadio.setUserData(SearchSortViewModel.Field.REG_NUMBER);
        rankRadio.setToggleGroup(fieldGroup);
        rankRadio.setUserData(SearchSortViewModel.Field.RANK);

        ascendingRadio.getStyleClass().add("search-filter-radio");
        descendingRadio.getStyleClass().add("search-filter-radio");
        fullNameRadio.getStyleClass().add("search-filter-radio");
        regNumberRadio.getStyleClass().add("search-filter-radio");
        rankRadio.getStyleClass().add("search-filter-radio");

        applyButton.getStyleClass().add("button-primary");
        applyButton.getStyleClass().add("search-filter-apply-button");
        applyButton.setMaxWidth(Double.MAX_VALUE);
        applyButton.setDefaultButton(true);
        applyButton.setOnAction(event -> handleApply());

        Separator separator = new Separator();
        separator.getStyleClass().add("search-filter-separator");

        HBox applyRow = new HBox(applyButton);
        applyRow.setAlignment(Pos.CENTER);
        HBox.setHgrow(applyButton, Priority.ALWAYS);

        VBox content = new VBox(6,
                ascendingRadio,
                descendingRadio,
                separator,
                fullNameRadio,
                regNumberRadio,
                rankRadio,
                applyRow);
        VBox.setMargin(applyRow, new Insets(6, 0, 0, 0));
        content.setPadding(new Insets(0));
        content.setAlignment(Pos.TOP_LEFT);
        content.getStyleClass().add("search-filter-popup");
        content.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleApply();
                event.consume();
            }
        });
        return content;
    }

    /**
     * Emits currently selected options to the registered apply callback.
     */
    private void handleApply() {
        onApply.accept(readSelectedOptions());
    }

    /**
     * Applies sort options to popup toggle selections.
     *
     * @param options options to display as selected
     */
    private void selectOptions(SearchSortViewModel.SortOptions options) {
        SearchSortViewModel.SortOptions safe = SearchSortViewModel.normalize(options);
        if (safe.direction() == SearchSortViewModel.Direction.DESC) {
            directionGroup.selectToggle(descendingRadio);
        } else {
            directionGroup.selectToggle(ascendingRadio);
        }

        switch (safe.field()) {
            case REG_NUMBER -> fieldGroup.selectToggle(regNumberRadio);
            case RANK -> fieldGroup.selectToggle(rankRadio);
            case FULL_NAME -> fieldGroup.selectToggle(fullNameRadio);
        }
    }

    /**
     * Reads selected direction/field toggles into an immutable options object.
     *
     * @return currently selected sort options
     */
    private SearchSortViewModel.SortOptions readSelectedOptions() {
        SearchSortViewModel.Direction direction = directionGroup.getSelectedToggle() == null
                ? SearchSortViewModel.Direction.ASC
                : (SearchSortViewModel.Direction) directionGroup.getSelectedToggle().getUserData();
        SearchSortViewModel.Field field = fieldGroup.getSelectedToggle() == null
                ? SearchSortViewModel.Field.FULL_NAME
                : (SearchSortViewModel.Field) fieldGroup.getSelectedToggle().getUserData();
        return new SearchSortViewModel.SortOptions(field, direction);
    }
}
