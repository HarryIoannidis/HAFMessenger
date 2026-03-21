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
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Search-filter UI and interaction flow (popup + first-search apply gate).
 */
final class SearchFilterController {

    interface SearchExecutor {
        boolean execute(String query, SearchSortViewModel.SortOptions sortOptions);
    }

    interface PopupPort {
        void show(Node anchor, SearchSortViewModel.SortOptions initialOptions, boolean focusApply,
                Consumer<SearchSortViewModel.SortOptions> onApply);

        void hide();

        boolean isShowing();
    }

    static final class FlowController {

        private final PopupPort popup;
        private final SearchExecutor searchExecutor;
        private final Runnable onSearchExecuted;

        private boolean requireApplyBeforeSearch = true;
        private SearchSortViewModel.SortOptions selectedOptions = SearchSortViewModel.SortOptions.DEFAULT;
        private String pendingQuery = "";

        FlowController(PopupPort popup, SearchExecutor searchExecutor, Runnable onSearchExecuted) {
            this.popup = Objects.requireNonNull(popup, "popup");
            this.searchExecutor = Objects.requireNonNull(searchExecutor, "searchExecutor");
            this.onSearchExecuted = Objects.requireNonNull(onSearchExecuted, "onSearchExecuted");
        }

        void onSearchTabActivated() {
            requireApplyBeforeSearch = true;
            pendingQuery = "";
            popup.hide();
        }

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

        void onFilterButtonTrigger(String query, Node anchor) {
            pendingQuery = normalizeQuery(query);
            if (popup.isShowing()) {
                popup.hide();
                return;
            }
            openPopup(anchor, true);
        }

        void onClear() {
            pendingQuery = "";
            requireApplyBeforeSearch = true;
            popup.hide();
        }

        SearchSortViewModel.SortOptions currentSortOptions() {
            return selectedOptions;
        }

        boolean isApplyRequiredBeforeSearch() {
            return requireApplyBeforeSearch;
        }

        private void openPopup(Node anchor, boolean focusApply) {
            popup.show(anchor, selectedOptions, focusApply, this::applySelection);
        }

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

        private void executeSearch(String query) {
            if (searchExecutor.execute(query, selectedOptions)) {
                onSearchExecuted.run();
            }
        }

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

    SearchFilterController(SearchExecutor searchExecutor, Runnable onSearchExecuted) {
        this.flow = new FlowController(new PopupPort() {
            @Override
            public void show(Node anchor, SearchSortViewModel.SortOptions initialOptions, boolean focusApply,
                    Consumer<SearchSortViewModel.SortOptions> onApply) {
                showPopupInternal(anchor, initialOptions, focusApply, onApply);
            }

            @Override
            public void hide() {
                menu.hide();
            }

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

    void onSearchTabActivated() {
        flow.onSearchTabActivated();
    }

    void onSearchTrigger(String query, Node anchor) {
        flow.onSearchTrigger(query, anchor);
    }

    void onFilterButtonTrigger(String query, Node anchor) {
        flow.onFilterButtonTrigger(query, anchor);
    }

    void onClear() {
        flow.onClear();
    }

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
        applyButton.setMinWidth(108);
        applyButton.setPrefWidth(108);
        applyButton.setMaxWidth(108);
        applyButton.setDefaultButton(true);
        applyButton.setOnAction(event -> handleApply());

        Separator separator = new Separator();
        separator.getStyleClass().add("search-filter-separator");

        HBox applyRow = new HBox(applyButton);
        applyRow.setAlignment(Pos.CENTER);

        VBox content = new VBox(6,
                ascendingRadio,
                descendingRadio,
                separator,
                fullNameRadio,
                regNumberRadio,
                rankRadio,
                applyRow);
        content.setPadding(new Insets(4));
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

    private void handleApply() {
        onApply.accept(readSelectedOptions());
    }

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
