package com.haf.client.viewmodels;

import com.haf.client.utils.UiConstants;
import com.haf.shared.dto.UserSearchResultDTO;

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

    private SearchSortViewModel() {
    }

    public static SortOptions normalize(SortOptions options) {
        return options == null ? SortOptions.DEFAULT : options;
    }

    public static Comparator<UserSearchResultDTO> comparator(SortOptions options) {
        SortOptions active = normalize(options);
        Comparator<UserSearchResultDTO> comparator = switch (active.field()) {
            case FULL_NAME -> SearchSortViewModel::compareByFullName;
            case REG_NUMBER -> SearchSortViewModel::compareByRegNumber;
            case RANK -> SearchSortViewModel::compareByRank;
        };

        if (active.direction() == Direction.DESC) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(SearchSortViewModel::safeUserId);
    }

    private static int compareByFullName(UserSearchResultDTO left, UserSearchResultDTO right) {
        return compareText(
                left == null ? null : left.getFullName(),
                right == null ? null : right.getFullName());
    }

    private static int compareByRegNumber(UserSearchResultDTO left, UserSearchResultDTO right) {
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

    private static int compareByRank(UserSearchResultDTO left, UserSearchResultDTO right) {
        String leftRank = left == null ? null : left.getRank();
        String rightRank = right == null ? null : right.getRank();
        int rankCompare = Integer.compare(rankWeight(leftRank), rankWeight(rightRank));
        if (rankCompare != 0) {
            return rankCompare;
        }
        return compareText(leftRank, rightRank);
    }

    private static int rankWeight(String rank) {
        if (rank == null) {
            return Integer.MAX_VALUE;
        }
        return RANK_WEIGHTS.getOrDefault(rank, Integer.MAX_VALUE);
    }

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
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int compareText(String left, String right) {
        return normalizeText(left).compareTo(normalizeText(right));
    }

    private static String safeUserId(UserSearchResultDTO dto) {
        return normalizeText(dto == null ? null : dto.getUserId());
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
