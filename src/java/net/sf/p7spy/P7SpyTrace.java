package net.sf.p7spy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.log4j.Logger;

/** Contains methods to determine whether to display a stack trace when a particular SQL statement is executed,
 * and to format parameters passed to JDBC methods.
 * 
 * <p>The <tt>p7spy-config.properties</tt> file is polled by this class, and the "matchText" property is read 
 * from this file. The value of this property is used to create a regular expression that is matched against
 * every SQL statement invoked through this JDBC driver; if the expression matches, then a dummy RuntimeException
 * is generated and logged.
 * 
 * <p>e.g. the file:
 * <pre>
 * matchText=SELECT\\s+.*\\s+FROM\\s+tblSomething 
 * </pre>
 * 
 * <p>would match all SELECT statements from tblSomething (where that table name is the first specified after
 * the 'FROM' keyword). 
 * 
 * @author knoxg
 *
 */
public class P7SpyTrace {

	/** Logger instance for this class */
	static Logger logger = Logger.getLogger(P7SpyTrace.class);
	
    /** A regex used to test against SQL */
    private static String matchPatternString;
    
    /** The {@link java.text.Pattern} form of {@link #matchPatternString} */
    private static Pattern matchPattern;
    
    /** Time in msec since epoch since the p7spy-config.properties file was read */
    private static long lastConfigLoadTime = -1;

    /** Returns true if the supplied SQL string is to trigger a stacktrace, false otherwise
     * 
     * @param arg the SQL to be run
     * 
     * @return true if the supplied SQL string is to trigger a stacktrace, false otherwise
     */
    public static boolean matchesArg(String arg) {
        if (System.currentTimeMillis() - lastConfigLoadTime > 30000) {
            try {
                synchronized(P7SpyTrace.class) {
                    lastConfigLoadTime = System.currentTimeMillis();
                    String newMatchPatternString = null;
                    File trapConfig = new File("p7spy-config.properties");
                    logger.debug("Reloading config from '" + trapConfig.getCanonicalFile() + "'");
                    if (trapConfig.exists()) {
                         // load match string from filesystem
                        InputStream is = new FileInputStream(trapConfig);
                        Properties props = new Properties();
                        props.load(is);
                        is.close();
                        newMatchPatternString = props.getProperty("matchText");
                    } else {
                        newMatchPatternString = null;
                    }
                    if (newMatchPatternString==null && matchPattern!=null) {
                        logger.debug("Disabling SQL matching");
                        matchPatternString = null;
                        newMatchPatternString = null;
                    } else if (newMatchPatternString != null && !newMatchPatternString.equals(matchPatternString)) {
                        logger.debug("Enabling SQL matching on '" + matchPatternString + "'");
                        matchPatternString = newMatchPatternString;
                        matchPattern = Pattern.compile(newMatchPatternString);
                    }
                }
            } catch (IOException ioe) {
                logger.warn("SQL matching disabled: " + ioe.getMessage());
            } catch (PatternSyntaxException pse) {
                logger.warn("SQL matching disabled: " + pse.getMessage());
            }
        }
        if (matchPattern!=null) { return matchPattern.matcher(arg).matches(); }
        return false;
    };

    /** Convert the supplied parameter into a form that will be written by a Logger object 
     * 
     * @param obj object to format
     * 
     * @return a String representation of this object
     */
    public static String formatResult(Object obj) {
    	if (obj==null) { return "null"; } 
    	else if (obj instanceof CharSequence) {
    		DecimalFormat unicodeDecimalFormat = new DecimalFormat("0000");
    		StringBuffer sb = new StringBuffer();
    		CharSequence cs = (CharSequence) obj;
    		int len = cs.length();
           sb.append("\"");
    		for (int i=0; i<len; i++) {
    			char ch = cs.charAt(i);
    			// assumes ASCII; check printable escape sequences first
    			if (ch == '\"') {
    				sb.append("\\\"");
    			} else if (ch == '\'') {
    				sb.append("\\'"); 
    			} else if (ch == '\\') {
    				sb.append("\\\\");
    			} else if (ch >= ' ' && ch <= '~') {
    				sb.append(ch);
    			} else if (ch == '\n') {
    				sb.append("\\n");
    			} else if (ch == '\r') {
    				sb.append("\\r");
    			} else if (ch == '\t') {
    				sb.append("\\t");
    			} else if (ch == '\b') {
    				sb.append("\\b");
    			} else {
    				sb.append("\\u");
    				sb.append(unicodeDecimalFormat.format(ch)); 
    			}
    		}
           sb.append("\"");
    		return sb.toString();
    	} else {
    		return obj.toString();
    	}
    }
	
}
