package com.haf.client.utils;

/**
 * Resolves rank labels to their corresponding icon resource paths.
 */
public final class RankIconResolver {

    /**
     * Prevents instantiation of this static utility.
     */
    private RankIconResolver() {
    }

    /**
     * Maps a rank label to its icon path.
     *
     * @param rank rank label
     * @return icon resource path for the provided rank, or default icon path
     */
    public static String resolve(String rank) {
        if (rank == null || rank.isBlank()) {
            return UiConstants.ICON_RANK_DEFAULT;
        }

        return switch (rank) {
            case UiConstants.RANK_YPOSMINIAS -> UiConstants.ICON_RANK_YPOSMINIAS;
            case UiConstants.RANK_SMINIAS -> UiConstants.ICON_RANK_SMINIAS;
            case UiConstants.RANK_EPISMINIAS -> UiConstants.ICON_RANK_EPISMINIAS;
            case UiConstants.RANK_ARCHISMINIAS -> UiConstants.ICON_RANK_ARCHISMINIAS;
            case UiConstants.RANK_ANTHYPASPISTIS -> UiConstants.ICON_RANK_ANTHYPASPISTIS;
            case UiConstants.RANK_ANTHYPOSMINAGOS -> UiConstants.ICON_RANK_ANTHYPOSMINAGOS;
            case UiConstants.RANK_YPOSMINAGOS -> UiConstants.ICON_RANK_YPOSMINAGOS;
            case UiConstants.RANK_SMINAGOS -> UiConstants.ICON_RANK_SMINAGOS;
            case UiConstants.RANK_EPISMINAGOS -> UiConstants.ICON_RANK_EPISMINAGOS;
            case UiConstants.RANK_ANTISMINARCHOS -> UiConstants.ICON_RANK_ANTISMINARCHOS;
            case UiConstants.RANK_SMINARCHOS -> UiConstants.ICON_RANK_SMINARCHOS;
            case UiConstants.RANK_TAKSIARCOS -> UiConstants.ICON_RANK_TAKSIARCOS;
            case UiConstants.RANK_YPOPTERARCHOS -> UiConstants.ICON_RANK_YPOPTERARCHOS;
            case UiConstants.RANK_ANTIPTERARCHOS -> UiConstants.ICON_RANK_ANTIPTERARCHOS;
            case UiConstants.RANK_PTERARCHOS -> UiConstants.ICON_RANK_PTERARCHOS;
            default -> UiConstants.ICON_RANK_DEFAULT;
        };
    }
}
