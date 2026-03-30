package com.haf.shared.requests;

/**
 * Request payload for updating whether presence indicators are hidden.
 */
public class PresenceVisibilityRequest {
    private boolean hidePresenceIndicators;

    /**
     * Creates an empty request for JSON deserialization.
     */
    public PresenceVisibilityRequest() {
        // Required for JSON deserialization.
    }

    /**
     * Creates a presence-visibility request.
     *
     * @param hidePresenceIndicators {@code true} to hide presence indicators
     */
    public PresenceVisibilityRequest(boolean hidePresenceIndicators) {
        this.hidePresenceIndicators = hidePresenceIndicators;
    }

    /**
     * Returns whether presence indicators should be hidden.
     *
     * @return hidden-presence flag
     */
    public boolean isHidePresenceIndicators() {
        return hidePresenceIndicators;
    }

    /**
     * Sets whether presence indicators should be hidden.
     *
     * @param hidePresenceIndicators hidden-presence flag
     */
    public void setHidePresenceIndicators(boolean hidePresenceIndicators) {
        this.hidePresenceIndicators = hidePresenceIndicators;
    }
}
