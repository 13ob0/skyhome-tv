package tv.skyhome;

public final class FocusNavigationTest {
    private static void check(boolean value,String message) {
        if(!value)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        int checks=0;
        for(int columns:new int[]{1,3,Typography.GRID_COLUMNS})for(int count=1;count<=129;count++) {
            for(int index=0;index<count;index++) {
                int up=FocusNavigation.verticalTarget(index,count,columns,true);
                int down=FocusNavigation.verticalTarget(index,count,columns,false);
                check(up>=0 && up<count && down>=0 && down<count,"out of bounds");
                check(up%columns==index%columns && down%columns==index%columns,"changed column");
                check(FocusNavigation.verticalTarget(up,count,columns,false)==index,"Up/Down not inverse");
                check(FocusNavigation.verticalTarget(down,count,columns,true)==index,"Down/Up not inverse");
                int position=index, columnCount=(count-1-index%columns)/columns+1;
                for(int step=1;step<=columnCount;step++) {
                    position=FocusNavigation.verticalTarget(position,count,columns,false);
                    check((position==index)==(step==columnCount),"column cycle omitted or repeated items");
                }
                checks++;
            }
        }
        check(FocusNavigation.verticalTarget(0,8,3,true)==6,"three-column partial row Up");
        check(FocusNavigation.verticalTarget(7,8,3,false)==1,"three-column partial row Down");
        check(FocusNavigation.verticalTarget(2,8,3,true)==5,"short final column Up");
        check(FocusNavigation.verticalTarget(1,6,4,true)==5,"partial row Up");
        check(FocusNavigation.verticalTarget(5,6,4,false)==1,"partial row Down");
        check(FocusNavigation.verticalTarget(2,6,4,true)==2,"single-item column");
        check(FocusNavigation.verticalTarget(0,1,1,false)==0,"single-item list");
        System.out.println("PASS "+checks+" navigation cases: lists, columns, partial rows, one item and wrap inverses");
    }
}
