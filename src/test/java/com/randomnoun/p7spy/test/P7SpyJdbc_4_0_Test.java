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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import com.randomnoun.p7spy.P7SpyDriver;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for p7spy. Should test in 1.6 VMs.
 * 
 * This unit test will include all tests in {@link com.randomnoun.p7spy.test.P7SpyJdbc_3_0_Test}.
 * 
 * @TODO increase coverage to include all SQL datatypes / JDBC methods
 * @TODO call the 14 tests.
 */
public class P7SpyJdbc_4_0_Test 
    extends TestCase
{
	
	P7SpyJdbc_3_0_Test p7SpyTest30 = new P7SpyJdbc_3_0_Test("P7SpyTests for JDK14");

	/** Logger instance for this class */
	public static Logger logger = Logger.getLogger(P7SpyJdbc_4_0_Test.class);
	
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public P7SpyJdbc_4_0_Test( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( P7SpyJdbc_4_0_Test.class );
    }

    public void setUp() {
    	p7SpyTest30.setUp();
    }
    
    public void testPlainConnection14() throws ClassNotFoundException, SQLException {
    	p7SpyTest30.testPlainConnection();
    }
    
    public void testP7SpyConnection14() throws ClassNotFoundException, SQLException {
    	p7SpyTest30.testP7SpyConnection();
    }
    
    // @TODO add some 4.0-specific tests here
    
}


