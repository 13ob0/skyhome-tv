package tv.skyhome;

/** Vertical navigation along actual items in a column, including a partial last row. */
final class FocusNavigation {
    static int verticalTarget(int index, int count, int columns, boolean up) {
        if(count<=0 || columns<=0 || index<0 || index>=count)throw new IllegalArgumentException();
        if(up)return index>=columns?index-columns:index+((count-1-index)/columns)*columns;
        return index+columns<count?index+columns:index%columns;
    }
}
