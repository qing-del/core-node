package com.jacolp.context;

public class BindEachRowContext {
    private static ThreadLocal<Integer> affectedRows = new ThreadLocal<>();
    public static void setAffectedRows(Integer affectedRows) {
        BindEachRowContext.affectedRows.set(affectedRows);
    }
    public static Integer getAffectedRows() {
        return BindEachRowContext.affectedRows.get();
    }
    public static void clear() {
        BindEachRowContext.affectedRows.remove();
    }
}
