package tv.skyhome;

/** Shared three-step text scale and layout metrics, in sp and dp respectively. */
final class Typography {
    static final int GRID_COLUMNS = 4;
    private static final int[] HOME = {30, 34, 38}, MENU = {26, 28, 32};
    private static final int[] GRID = {24, 26, 30}, FOOTER = {22, 24, 26};
    private static final int[] HOME_HEIGHT = {58, 62, 68}, MENU_HEIGHT = {62, 66, 74};
    private static final int[] GRID_HEIGHT = {94, 100, 112};
    static int home(int step) { return HOME[step]; }
    static int menu(int step) { return MENU[step]; }
    static int grid(int step) { return GRID[step]; }
    static int footer(int step) { return FOOTER[step]; }
    static int homeHeight(int step) { return HOME_HEIGHT[step]; }
    static int menuHeight(int step) { return MENU_HEIGHT[step]; }
    static int gridHeight(int step) { return GRID_HEIGHT[step]; }
    private Typography() { }
}
