package com.haf.client.viewmodels;

import com.haf.client.utils.UiConstants;
import com.haf.shared.dto.UserSearchResult;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Search result sorting model + comparator logic.
 */
public final class SearchSortViewModel {

    public enum Field {
        FULL_NAME,
        REG_NUMBER,
        RANK
    }

    public enum Direction {
        ASC,
        DESC
    }

    public record SortOptions(Field field, Direction direction) {
        public static final SortOptions DEFAULT = new SortOptions(Field.FULL_NAME, Direction.ASC);

        /**
         * Canonical constructor that enforces non-null sort field and direction.
         *
         * @param field     sorting field to use
         * @param direction sorting direction to apply
         */
        public SortOptions {
            field = Objects.requireNonNullElse(field, Field.FULL_NAME);
            direction = Objects.requireNonNullElse(direction, Direction.ASC);
        }
    }

    private static final Map<String, Integer> RANK_WEIGHTS = Map.ofEntries(
            Map.entry(UiConstants.RANK_YPOSMINIAS, 1),
            Map.entry(UiConstants.RANK_SMINIAS, 2),
            Map.entry(UiConstants.RANK_EPISMINIAS, 3),
            Map.entry(UiConstants.RANK_ARCHISMINIAS, 4),
            Map.entry(UiConstants.RANK_ANTHYPASPISTIS, 5),
            Map.entry(UiConstants.RANK_ANTHYPOSMINAGOS, 6),
            Map.entry(UiConstants.RANK_YPOSMINAGOS, 7),
            Map.entry(UiConstants.RANK_SMINAGOS, 8),
            Map.entry(UiConstants.RANK_EPISMINAGOS, 9),
            Map.entry(UiConstants.RANK_ANTISMINARCHOS, 10),
            Map.entry(UiConstants.RANK_SMINARCHOS, 11),
            Map.entry(UiConstants.RANK_TAKSIARCOS, 12),
            Map.entry(UiConstants.RANK_YPOPTERARCHOS, 13),
            Map.entry(UiConstants.RANK_ANTIPTERARCHOS, 14),
            Map.entry(UiConstants.RANK_PTERARCHOS, 15));

    /**
     * Prevents instantiation of this static sorting utility.
     */
    private SearchSortViewModel() {
    }

    /**
     * Normalizes nullable sort options to a default configuration.
     *
     * @param options incoming sort options
     * @return provided options or {@link SortOptions#DEFAULT} when {@code null}
     */
    public static SortOptions normalize(SortOptions options) {
        return options == null ? SortOptions.DEFAULT : options;
    }

    /**
     * Builds a comparator for user search results based on field + direction.
     *
     * @param options sorting options to apply
     * @return comparator with stable tie-breaker on user id
     */
    public static Comparator<UserSearchResult> comparator(SortOptions options) {
        SortOptions active = normalize(options);
        Comparator<UserSearchResult> comparator = switch (active.field()) {
            case FULL_NAME -> SearchSortViewModel::compareByFullName;
            case REG_NUMBER -> SearchSortViewModel::compareByRegNumber;
            case RANK -> SearchSortViewModel::compareByRank;
        };

        if (active.direction() == Direction.DESC) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(SearchSortViewModel::safeUserId);
    }

    /**
     * Compares two results by full name.
     *
     * @param left  left result
     * @param right right result
     * @return comparator result for full-name ordering
     */
    private static int compareByFullName(UserSearchResult left, UserSearchResult right) {
        return compareText(
                left == null ? null : left.getFullName(),
                right == null ? null : right.getFullName());
    }

    /**
     * Compares two results by registration number, preferring numeric comparison
     * when possible.
     *
     * @param left  left result
     * @param right right result
     * @return comparator result for registration-number ordering
     */
    private static int compareByRegNumber(UserSearchResult left, UserSearchResult right) {
        Integer leftNumeric = parseRegNumber(left == null ? null : left.getRegNumber());
        Integer rightNumeric = parseRegNumber(right == null ? null : right.getRegNumber());
        if (leftNumeric != null && rightNumeric != null) {
            int numericCompare = Integer.compare(leftNumeric, rightNumeric);
            if (numericCompare != 0) {
                return numericCompare;
            }
        }

        return compareText(
                left == null ? null : left.getRegNumber(),
                right == null ? null : right.getRegNumber());
    }

    /**
     * Compares two results by military rank priority and rank text as fallback.
     *
     * @param left  left result
     * @param right right result
     * @return comparator result for rank ordering
     */
    private static int compareByRank(UserSearchResult left, UserSearchResult right) {
        String leftRank = left == null ? null : left.getRank();
        String rightRank = right == null ? null : right.getRank();
        int rankCompare = Integer.compare(rankWeight(leftRank), rankWeight(rightRank));
        if (rankCompare != 0) {
            return rankCompare;
        }
        return compareText(leftRank, rightRank);
    }

    /**
     * Maps a rank label to a sortable weight.
     *
     * @param rank rank label to evaluate
     * @return numeric weight, or {@link Integer#MAX_VALUE} for unknown/empty rank
     */
    private static int rankWeight(String rank) {
        if (rank == null) {
            return Integer.MAX_VALUE;
        }
        return RANK_WEIGHTS.getOrDefault(rank, Integer.MAX_VALUE);
    }

    /**
     * Parses a registration number into an integer when possible.
     *
     * @param regNumber registration number text
     * @return parsed integer value, or {@code null} when not numeric
     */
    private static Integer parseRegNumber(String regNumber) {
        if (regNumber == null) {
            return null;
        }
        String trimmed = regNumber.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(trimmed);
        } catch (NumberFormatException _) {
            return null;
        }
    }

    /**
     * Compares two text values using normalized lowercase variants.
     *
     * @param left  left text
     * @param right right text
     * @return lexical comparison result
     */
    private static int compareText(String left, String right) {
        return normalizeText(left).compareTo(normalizeText(right));
    }

    /**
     * Returns a normalized user id used as a deterministic tie-breaker.
     *
     * @param dto search result entry
     * @return normalized user id string
     */
    private static String safeUserId(UserSearchResult dto) {
        return normalizeText(dto == null ? null : dto.getUserId());
    }

    /**
     * Normalizes text for case-insensitive comparison.
     *
     * @param value source text
     * @return trimmed lowercase value, or empty string when null
     */
    private static String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
