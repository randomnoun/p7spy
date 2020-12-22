package com.randomnoun.p7spy;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Properties;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/** A JDBC tracing driver based loosely on the p6spy toolkit.  
 * 
 * <p>This driver intercepts calls to other JDBC drivers, and performs logging via log4j. 
 * Included in the logs are parameter values (including SQL), object instance data, and time taken to perform each method. 
 * At runtime, it is possible
 * to configure the driver to generate stack traces when particular SQL statements are detected by the
 * driver, to assist in debugging.
 * 
 * <p>To use, place the p7spy classes on the classpath, and place a prefix in the JDBC connection string. 
 * Multiple prefixes can be used to chain this driver with other drivers (e.g. jdbinsight)
 * 
 * <p>The prefix to use is "<tt>p7spy:</tt>" , which you should place between the "<tt>jdbc:</tt>" prefix and 
 * the rest of the connection string, e.g.
 * 
 * <table>
 * <tr><th>Connection string</th>
 *     <th>Wrapped connection string</th>
 * <tr><td><tt>jdbc:mysql://localhost/test</tt></td>
 *     <td><tt>jdbc:p7spy:mysql://localhost/test</tt></td>
 * <tr><td><tt>jdbc:oracle:thin:@localhost:1521:TEST</tt></td>
 *     <td><tt>jdbc:p7spy:oracle:thin:@localhost:1521:TEST</tt></td>
 * </table>
 * 
 * <p>The driver you should register with the Java DriverManager is <tt>net.sf.p7spy.P7Driver</tt> (i.e. this class). If the
 * driver to be wrapped also needs to be registered, it can be included within the connection string 
 * after the 'p7spy' component, separated by a '#'; e.g.:
 *
 * <table>
 * <tr><th>Connection string</th>
 *     <th>Wrapped connection string</th>
 * <tr><td><tt>jdbc:mysql://localhost/test</tt></td>
 *     <td><tt>jdbc:p7spy#com.mysql.jdbc.Driver:mysql://localhost/test</tt></td>
 * <tr><td><tt>jdbc:oracle:thin:@localhost:1521:TEST</tt></td>
 *     <td><tt>jdbc:p7spy#oracle.jdbc.driver.OracleDriver:oracle:thin:@localhost:1521:TEST</tt></td>
 * </table>
 *  
 * <p>If you're wrapping a proprietary driver that already wraps another connection (and doesn't use a <tt>jdbc:</tt> prefix) 
 * then you should place a "-:" at the start of the wrapped connection string. For example:
 *
 * <table>
 * <tr><th>Connection string</th>
 *     <th>Wrapped connection string</th>
 * <tr><td><tt>weirdProtocol:jdbc:oracle:thin:@localhost:1521:TEST<tt></td>
 *     <td><tt>jdbc:p7spy#com.WeirdProtocolDriver:-:weirdProtocol:jdbc:oracle:thin:@localhost:1521:TEST<tt></td>
 * </table>
 * 
 * <p>Different connection implementations are supplied depending on the VM in use (If stubs are compiled 
 * in a 1.6 VM, they will throws 1.6 exceptions, which cause problems in a 1.5 VM. Conversely, if compiled 
 * in a 1.5 VM, the generated stubs will not include methods introduced in later versions of the JDBC standard).
 *
 * <p><i>Implementation note:</i> The implementation of these wrappers do not use aspects, because I hate 
 * debugging through things like
 * Proxy$12893, and it allows me to fine-tune the generated code without having
 * to worry about inevitable classloader conflicts. 
 * (The wrapper interfaces are generated using the {@link com.randomnoun.p7spy.generator.ClassStubGenerator} class).
 * 
 * @author knoxg
 */
public class P7SpyDriver implements Driver {

	/** Major version number reported by {@link #getMajorVersion()} */
	public static final int MAJOR_VERSION = 2;

	/** {@inheritDoc} */
	public boolean acceptsURL(String url) throws SQLException {
		return url.startsWith("jdbc:p7spy:") || url.startsWith("jdbc:p7spy#");
	}
	
    /** Logger used to dump method invocations */
    private static final Logger logger = Logger.getLogger(P7SpyDriver.class);

    /** {@inheritDoc} */
	public Connection connect(String url, Properties info) throws SQLException {
		logger.debug("P7SpyDriver.connect('" + url + "', " + info.toString());
		String wrappedUrl;
		if (url.startsWith("jdbc:p7spy:")) {
			url = url.substring(11);
		} else if (url.startsWith("jdbc:p7spy#")) {
			url = url.substring(11);
			if (url.indexOf(":")==-1) {
				throw new SQLException("Invalid p7spy syntax for url '" + url + "'");
			} else {
				String driverClass = url.substring(0, url.indexOf(":"));
				url = url.substring(url.indexOf(":")+1);
				try {
					Class.forName(driverClass);
				} catch (ClassNotFoundException cnfe) {
					// requires more recent version of java
					// throw new SQLException("Could not initialise '" + driverClass + "' driver", cnfe);
					throw (SQLException) new SQLException("Could not initialise '" + driverClass + "' driver").initCause(cnfe);
				}
			}
		} else {
			// this shouldn't happen if acceptsURL() is being called by the DriverManager, 
			// but apparently it still does. Could be a JVM1.5 thing.  
			return null;
		}
		
		if (url.startsWith("-:")) {
			wrappedUrl = url.substring(2);
		} else {
		    wrappedUrl = "jdbc:" + url;
		}
		
		// if I decide to retrofit support for old JDKs again:
		
		// now going to use the JDBC spec standard number rather than the JDK version
		// because they haven't decided to renumber it half-way through.
		/*
		Connection wrappedConnection = DriverManager.getConnection(wrappedUrl, info);
		String connectionClass = "net.sf.p7spy.jdbc_3_0.P7Connection"; // was impl14
		try {
			Method m = Statement.class.getMethod("isPoolable", new Class[] {});
			// if this didn't throw an exception, we can use the impl16 class
			connectionClass = "net.sf.p7spy.jdbc_4_0.P7Connection"; // was impl16
			
			
			
		} catch (Exception e) {
			// safe to ignore
		}
		*/
		
		Connection wrappedConnection = DriverManager.getConnection(wrappedUrl, info);
		String connectionClass = "com.randomnoun.p7spy.jdbc_4_3.P7Connection"; // JDK 9+
		
		Connection conn = null;
		try {
			Class clazz = Class.forName(connectionClass);
			conn = (Connection) clazz.getConstructor(new Class[] { Connection.class }).newInstance( new Object[] { wrappedConnection });
		} catch (ClassNotFoundException cnfe) {
			throw (SQLException) new SQLException("Could not initialise '" + connectionClass + "' wrapper").initCause(cnfe);
		} catch (IllegalArgumentException iae) {
			throw (SQLException) new SQLException("Could not initialise '" + connectionClass + "' wrapper").initCause(iae);
		} catch (SecurityException se) {
			throw (SQLException) new SQLException("Could not initialise '" + connectionClass + "' wrapper").initCause(se);
		} catch (InstantiationException ie) {
			throw (SQLException) new SQLException("Could not initialise '" + connectionClass + "' wrapper").initCause(ie);
		} catch (IllegalAccessException iae) {
			throw (SQLException) new SQLException("Could not initialise '" + connectionClass + "' wrapper").initCause(iae);
		} catch (InvocationTargetException ite) {
			throw (SQLException) new SQLException("Could not initialise '" + connectionClass + "' wrapper").initCause(ite);
		} catch (NoSuchMethodException nsme) {
			throw (SQLException) new SQLException("Could not initialise '" + connectionClass + "' wrapper").initCause(nsme);
		}
		
		return conn;
	}
	

	/** Major version required by JDBC contract */
	public int getMajorVersion() {
		return MAJOR_VERSION;
	}

	/** Minor version required by JDBC contract. This is the buildNumber of the p7spy JAR, 
	 * or 0 if not an 'official' build.
	 */
	public int getMinorVersion() {
		/*
		int buildNumber;
		try {
			InputStream is = P7SpyDriver.class.getClassLoader().getResourceAsStream("p7spyBuild.properties");
			Properties props = new Properties();
			props.load(is);
			String buildNumberString = props.getProperty("build.buildNumber");
			buildNumber = Integer.parseInt(buildNumberString);
		} catch (Exception e) {
			buildNumber = 0;
			throw new RuntimeException(e);
		}
		return buildNumber;
		*/
		return 0;
	}

	/** Returns the DriverPropertyInfo of the wrapped connection */
	public DriverPropertyInfo[] getPropertyInfo(String url, Properties info)
			throws SQLException {
		// probably need to do same url mangling as above
		String realUrl = url.substring(6);
		Driver realDriver = DriverManager.getDriver(realUrl);
		return realDriver.getPropertyInfo(url, info);
	}

	/** This thing isn't JDBC-compliant, which probably involves running it through
	 * a JCP test kit or something. */
	public boolean jdbcCompliant() {
		return false;
	}
	
	static {
		try {
			DriverManager.registerDriver(new P7SpyDriver());
		} catch (SQLException sqle) {
			Logger.getLogger(P7SpyDriver.class).error("Could not register p7spy driver", sqle);
		}
	}

	// from JDK 11. Should probably just construct a new one.
	/** Logger required by JDBC contract for some reason */
	public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
		return java.util.logging.Logger.getLogger(P7SpyDriver.class.getName());
	}

}