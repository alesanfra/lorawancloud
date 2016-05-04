package iet.unipi.Lora.NetworkServer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Created by alessio on 04/05/16.
 */
public class SimpleDateFormatter extends Formatter {

    @Override
    public String format(LogRecord record) {
        StringBuilder builder = new StringBuilder();
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
