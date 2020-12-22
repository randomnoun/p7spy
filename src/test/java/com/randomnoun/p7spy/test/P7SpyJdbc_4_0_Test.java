package net.sf.p7spy.test;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import com.randomnoun.p7spy.P7SpyDriver;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for p7spy. Should test in 1.6 VMs.
 * 
 * This unit test will include all tests in {@link net.sf.p7spy.test.P7Spy14Test}.
 * 
 * @TODO increase coverage to include all SQL datatypes / JDBC methods
 * @TODO call the 14 tests.
 */
public class P7Spy16Test 
    extends TestCase
{
	
	P7Spy14Test p7SpyTest14 = new P7Spy14Test("P7SpyTests for JDK14");

	/** Logger instance for this class */
	public static Logger logger = Logger.getLogger(P7Spy16Test.class);
	
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public P7Spy16Test( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( P7Spy16Test.class );
    }

    public void setUp() {
    	p7SpyTest14.setUp();
    }
    
    public void testPlainConnection14() throws ClassNotFoundException, SQLException {
    	p7SpyTest14.testPlainConnection();
    }
    
    public void testP7SpyConnection14() throws ClassNotFoundException, SQLException {
    	p7SpyTest14.testP7SpyConnection();
    }
    
}

