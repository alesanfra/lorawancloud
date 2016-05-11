package iet.unipi.lorawan;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Created by alessio on 03/05/16.
 */
public class LogFormatter extends Formatter {

    @Override
    public String format(LogRecord record) {
        return record.getMessage().concat("\n");
    }

    private String calcDate(long millisecs) {
        SimpleDateFormat date_format = new SimpleDateFormat("YYYY-MM-dd HH:mm ");
        Date resultdate = new Date(millisecs);
        return date_format.format(resultdate);
    }
}