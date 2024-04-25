package com.viyable.util;

public class GenericConstants {
    public static final String YES = "Yes";
    public static final String NO = "No";

    public static String getTextForBoolean(boolean value){
        return value ? YES : NO;
    }
}
