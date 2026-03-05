package dev.cosgy.jmusicbot.playlist;

class BasePlaylistLoadError {
    private final int number;
    private final String item;
    private final String reason;

    protected BasePlaylistLoadError(int number, String item, String reason) {
        this.number = number;
        this.item = item;
        this.reason = reason;
    }

    public int getIndex() {
        return number;
    }

    public String getItem() {
        return item;
    }

    public String getReason() {
        return reason;
    }
}
