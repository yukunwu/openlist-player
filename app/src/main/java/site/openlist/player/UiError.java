package site.openlist.player;

/** Keeps protocol errors language-neutral until they reach the current Activity. */
final class UiError extends IllegalArgumentException {
    final int resource;
    final Object[] arguments;
    UiError(int resource, Object... arguments) {
        super("Request failed");
        this.resource = resource;
        this.arguments = arguments;
    }
}
