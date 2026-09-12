package ua.bookloom.document.mask;

/**
 * Where one restored placeholder fragment landed in the restored text, as a half-open {@code [start, end)}
 * character range — a value with equality, unlike the {@code int[]} pair it replaces.
 *
 * @param start the index of the fragment's first character; non-negative
 * @param end the index one past the fragment's last character; at least {@code start}
 */
public record FragmentRange(int start, int end) {

    public FragmentRange {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("not a half-open range: [" + start + ", " + end + ")");
        }
    }
}
