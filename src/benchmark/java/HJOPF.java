/**
 * HJOPF — paper entry point for HJ-OPF (Output-Sensitive Hash-Indexed Join).
 *
 * The full implementation currently resides in {@link FOMAblationFlags}
 * (historical class name). This thin entry point exists so that the
 * documentation and manuscript can refer to the class name {@code HJOPF}
 * while the code remains fully functional.
 *
 * Primary claim configuration:
 *   -Dmode=hash_only -DbitmapPolicy=never -DwsbPolicy=never
 *
 * Residual ablation (not a primary claim):
 *   -Dmode=adaptive  (and related adaptive* flags)
 */
public final class HJOPF {
    private HJOPF() {}

    public static void main(String[] args) throws Exception {
        FOMAblationFlags.main(args);
    }
}
