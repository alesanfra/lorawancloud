package iet.unipi.lorawan;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Format log by appending the record to a time stamp
 */

public class SimpleDateFormatter extends Formatter {

    private static final int BUFFER_LEN = 300;

    @Override
    public String format(LogRecord record) {
        StringBuilder builder = new StringBuilder(BUFFER_LEN);
        builder.append(calcDate(record.getMillis()));
        builder.append(record.getMessage());
        builder.append("\n");
        return builder.toString();
    }

    private String calcDate(long millisecs) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS  ");
        Date date = new Date(millisecs);
        return dateFormat.format(date);
    }
}
