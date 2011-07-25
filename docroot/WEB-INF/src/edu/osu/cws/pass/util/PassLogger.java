package edu.osu.cws.pass.util;

/**
 * Created by IntelliJ IDEA.
 * User: luf
 * Date: 7/23/11
 * Time: 4:42 PM
 * To change this template use File | Settings | File Templates.
 * This class wraps the edu.osu.ces.util.Logger class to provide some PASS specific fields.
 * It provides a way to be consistent across the application.
 * All PASS logging should be done by this class, not the native Logger class.
 */

import edu.osu.cws.util.Logger;
import java.util.HashMap;
import java.util.Map;

public class PassLogger {

    private Logger logger;
    private String appName = "PASS";

    private Map<String, String> fields = new HashMap<String, String>();


    public PassLogger(String serverName, String logName)
    {
        logger = new Logger(serverName, logName);
        fields.put("appName", "appName");
    }

    public void log(String level, String shortMessage, String longMessage,
                    Map<String,String> myFields) throws Exception
    {
        fields.putAll(myFields);
        logger.log(level, shortMessage, longMessage, fields);
    }


    public void log(String level, String shortMessage, String longMessage) throws Exception
    {
        logger.log(level, shortMessage, longMessage, fields);
    }

    //@todo: infinite recursive calling.
    public void log(String level, String shortMessage, Exception exception,
                    Map<String,String> myFields)  throws Exception
    {
        fields.putAll(myFields);
        log(level, shortMessage, exception, fields);
    }

    public void log(String level, String shortMessage, Exception exception)  throws Exception
    {
        log(level, shortMessage, exception, fields);
    }

}
