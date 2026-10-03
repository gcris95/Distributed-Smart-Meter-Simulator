package beans;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss:SSS");

    private TimeUtil() {}

    public static long millisSinceMidnight() {
        return LocalTime.now().toNanoOfDay() / 1_000_000;
    }

    public static String format(long millisSinceMidnight) {
        return LocalTime.ofNanoOfDay(millisSinceMidnight * 1_000_000).format(FORMAT);
    }
}