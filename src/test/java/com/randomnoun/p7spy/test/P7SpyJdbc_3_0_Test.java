package com.randomnoun.p7spy.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.spi.LoggingEvent;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import com.randomnoun.p7spy.P7SpyDriver;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for p7spy. Should test in 1.4, 1.5 and 1.6 VMs.
 * 
 * @TODO increase coverage to include all SQL datatypes / JDBC methods
 */
public class P7SpyJdbc_3_0_Test 
    extends TestCase
{

	/** Logger instance for this class */
	public static Logger logger = Logger.getLogger(P7SpyJdbc_3_0_Test.class);
	
	/** log4j Appender used to test generated log4j text */
	public static class MemoryAppender extends AppenderSkeleton {
	    private LinkedList loggingEvents;
	    public MemoryAppender() {
	        loggingEvents = new LinkedList();
	    }
	    public void setImmediateFlush(boolean value) { };
	    public boolean getImmediateFlush() { return true; }
	    public void activateOptions() {}
	    public void append(LoggingEvent event) {
	        if (!checkEntryConditions()) { return; }
	        subAppend(event);
	    }
	    protected boolean checkEntryConditions() { return true; }
	    public synchronized void close() {  loggingEvents.clear(); }
	    public synchronized void clear() { synchronized(loggingEvents) { loggingEvents.clear(); } }
	    protected void subAppend(LoggingEvent event) {
	        synchronized(loggingEvents) {
	        	// perhaps convert to string form first ?
		        loggingEvents.add(event);
	        }
	    }
	    public boolean requiresLayout() { return false; }
	    public List getLoggingEvents() { return new ArrayList(loggingEvents); }
	}

	public static String SQL_DROP_TABLE = "DROP TABLE wish_list";
    public static String SQL_CREATE_TABLE = 
    	"CREATE TABLE wish_list  " +
        "  (wish_id    INT         NOT NULL GENERATED ALWAYS AS IDENTITY CONSTRAINT wish_pk PRIMARY KEY, " + 
        "   entry_date TIMESTAMP   DEFAULT CURRENT_TIMESTAMP, " +
        "   wish_item  VARCHAR(32) NOT NULL)" ;
	
    public static String SQL_CREATE_ITEM = "INSERT INTO wish_list ( wish_item ) VALUES ( ? )";
    public static String SQL_SELECT_ITEM = "SELECT wish_item FROM wish_list WHERE wish_item = ?";
	
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public P7SpyJdbc_3_0_Test( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( P7SpyJdbc_3_0_Test.class );
    }

    public void setUp() {
		Properties props = new Properties();

		// output looks like:
		//   10:57:37,015, DEBUG [P7Connection@118cb3a          ] [   62] prepareStatement("INSERT INTO wish_list ( wish_item ) VALUES ( ? )"): com.randomnoun.p7spy.impl16.P7PreparedStatement@19ea173
        //   (timestamp)  (level) (object instance)               (time)  (method call: result)
		
		props.put("log4j.rootCategory", "INFO, CONSOLE, MEMORY");
		props.put("log4j.appender.CONSOLE", "org.apache.log4j.ConsoleAppender");
		props.put("log4j.appender.CONSOLE.layout", "org.apache.log4j.PatternLayout");
		props.put("log4j.appender.CONSOLE.layout.ConversionPattern", "%d{ABSOLUTE}, %-5p [%-30X{p7Id}] [%5X{p7Duration}] %m%n");
		props.put("log4j.appender.MEMORY", P7SpyJdbc_3_0_Test.MemoryAppender.class.getName());
		
		props.put("log4j.logger.com.randomnoun.p7spy", "DEBUG");		 
		PropertyConfigurator.configure(props);
    }
    
    /**
     * Non-traced test
     * 
     * @throws ClassNotFoundException 
     * @throws SQLException 
     */
    public void testPlainConnection() throws ClassNotFoundException, SQLException
    {
        // String driver = "org.apache.derby.jdbc.EmbeddedDriver";
    	String driver = "org.apache.derby.iapi.jdbc.AutoloadedDriver";
        String dbName = "p7spyTestDB";
        // String connectionURL = "jdbc:derby:" + dbName + ";create=true";
        String connectionURL = "jdbc:derby:memory:" + dbName + ";create=true";
        // Class.forName(driver); 
        Connection conn = DriverManager.getConnection(connectionURL);		 
		DataSource ds = new SingleConnectionDataSource(conn, true);
		JdbcTemplate jt = new JdbcTemplate(ds);
        
        // possibly drop table if it already exists (in-memory DB?)
		try {
            jt.execute(SQL_DROP_TABLE);
        } catch (DataAccessException dae) {
        	// ignore
        }
        jt.execute(SQL_CREATE_TABLE);
        jt.update(SQL_CREATE_ITEM, new Object[] { "thing" });
        List list = jt.queryForList(SQL_SELECT_ITEM, new Object[] { "thing" });
        Map row = (Map) list.get(0);
        assertEquals("thing", row.get("WISH_ITEM"));
    }

    /**
     * Non-traced test
     * 
     * @throws ClassNotFoundException 
     * @throws SQLException 
     */
    public void testP7SpyConnection() throws ClassNotFoundException, SQLException
    {
        String driver = "com.randomnoun.p7spy.P7SpyDriver";
        String dbName = "p7spyTestDB2";
        // String connectionURL = "jdbc:derby:" + dbName + ";create=true";
        // String connectionURL = "jdbc:p7spy#org.apache.derby.jdbc.EmbeddedDriver:derby:memory:" + dbName + ";create=true";
        
        String connectionURL = "jdbc:p7spy#org.apache.derby.iapi.jdbc.AutoloadedDriver:derby:memory:" + dbName + ";create=true";
        
        Class.forName(driver); 
        Connection conn = DriverManager.getConnection(connectionURL);		 
		DataSource ds = new SingleConnectionDataSource(conn, true);
		JdbcTemplate jt = new JdbcTemplate(ds);
        
        // possibly drop table if it already exists (in-memory DB?)
		try {
            jt.execute(SQL_DROP_TABLE);
        } catch (DataAccessException dae) {
        	// ignore
        }
        jt.execute(SQL_CREATE_TABLE);
        jt.update(SQL_CREATE_ITEM, new Object[] { "thing" });
        List list = jt.queryForList(SQL_SELECT_ITEM, new Object[] { "thing" });
        Map row = (Map) list.get(0);
        assertEquals("thing", row.get("WISH_ITEM"));

        // @TODO perform some assertions on the logging generated from the above
        MemoryAppender memoryAppender = (MemoryAppender) Logger.getRootLogger().getAppender("MEMORY");
        List events = memoryAppender.getLoggingEvents();
        assertTrue(events.size() > 0);
    }


    // this test never fails; it is just used to display the driver build number
    public void testDriverVersion() throws ClassNotFoundException, SQLException {
    	P7SpyDriver driver = new P7SpyDriver();
    	logger.info("P7Spy driver major version: " + driver.getMajorVersion());
    	logger.info("P7Spy driver minor version: " + driver.getMinorVersion());
    	
    }

}


