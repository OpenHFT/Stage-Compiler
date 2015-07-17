package net.openhft.sg;

public final class StringUtils {
    
    public static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
    
    public static String lowercase(String s) {
        return s.substring(0, 1).toLowerCase() + s.substring(1);
    }

    public static int greatestCommonPrefixLength(String a, String b) {
        int minLength = Math.min(a.length(), b.length());
        for (int i = 0; i < minLength; i++) {
            if (a.charAt(i) != b.charAt(i))
                return i;
        }
        return minLength;
    }
    
    private StringUtils() {}
}
