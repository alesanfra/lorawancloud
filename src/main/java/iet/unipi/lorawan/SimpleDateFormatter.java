package iet.unipi.lorawan;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Created by alessio on 04/05/16.
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
        SimpleDateFormat date_format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS  ");
        Date resultdate = new Date(millisecs);
        return date_format.format(resultdate);
    }
}
