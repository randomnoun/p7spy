
# p7spy

**p7spy**  is a thing that logs JDBC statements and queries via log4j. 

It's called p7spy because I was using p6spy back in the early 2000s and that didn't do something, although I can't remember what that something was any more. 

You can configure regular expressions that will cause full stacktraces to be logged whenever any SQL executes that matches that regex. That might be it.

## Why would anyone want to do that  ?

Because you're performing software support on an application with a few million lines of code, or that's generating SQL dynamically via AOP or something and you want to find out what the golly gosh darn heck it's doing.

## How do I use it

If you need to specify a driver class name somewhere, it's  `com.randomnoun.p7spy.P7SpyDriver`

Put it on the CLASSPATH and then shoehorn 'p7spy' into the JDBC connection string; e.g.
```
jdbc:mysql://localhost/test
```
becomes
```
jdbc:p7spy:mysql://localhost/test
```

or alternatively
```
jdbc:oracle:thin:@localhost:1521:TEST
```
becomes
```
jdbc:p7spy:oracle:thin:@localhost:1521:TEST
```
If you're using an ancient JDBC driver and also need to specify the class name of the JDBC driver that you're wrapping, then you can add that into the JDBC connection string as well; e.g.
```
jdbc:p7spy#oracle.jdbc.driver.OracleDriver:oracle:thin:@localhost:1521:TEST
```

## Maven 
If you're using maven, then add the following dependency to your pom.xml:
```
  <dependency>
	<groupId>com.randomnoun.p7spy</groupId>
	<artifactId>p7spy</artifactId>
	<version>2.0.0</version>
  </dependency>
```

## Output

p7spy produces one log line per JDBC method call. 

The MDC variables 'p7Id' and 'p7Duration' return a class identifier and the method duration, respectively.

You can tweak the output using log4j layout patterns. The unit tests, which use a ConversionPattern layout of `"%d{ABSOLUTE}, %-5p [%-30X{p7Id}] [%5X{p7Duration}] %m%n"` produce this kind of output:

```
16:50:02,098, INFO  [                              ] [     ] P7Spy driver major version: 2
16:50:02,102, INFO  [                              ] [     ] P7Spy driver minor version: 0
16:50:02,149, DEBUG [                              ] [     ] P7SpyDriver.connect('jdbc:p7spy#org.apache.derby.iapi.jdbc.AutoloadedDriver:derby:memory:p7spyTestDB2;create=true', {}
16:50:02,150, DEBUG [                              ] [     ] P7SpyDriver.connect('jdbc:derby:memory:p7spyTestDB2;create=true', {}
16:50:03,172, DEBUG [P7Connection@18b0930f         ] [    0] new Connection()
16:50:03,236, DEBUG [P7Connection@18b0930f         ] [    0] isClosed(): false
16:50:03,281, DEBUG [P7Statement@29df4d43          ] [    0] new Statement()
16:50:03,281, DEBUG [P7Connection@18b0930f         ] [   45] createStatement(): com.randomnoun.p7spy.jdbc_4_3.P7Statement@29df4d43
16:50:03,492, INFO  [P7Statement@29df4d43          ] [    0] Loading XML bean definitions from class path resource [org/springframework/jdbc/support/sql-error-codes.xml]
16:50:03,648, INFO  [P7Statement@29df4d43          ] [    0] SQLErrorCodes loaded: [DB2, Derby, H2, HSQL, Informix, MS-SQL, MySQL, Oracle, PostgreSQL, Sybase]
16:50:03,649, DEBUG [P7Connection@18b0930f         ] [    0] isClosed(): false
16:50:03,724, DEBUG [P7DatabaseMetaData@fac80      ] [    0] new DatabaseMetaData()
16:50:03,725, DEBUG [P7Connection@18b0930f         ] [   75] getMetaData(): com.randomnoun.p7spy.jdbc_4_3.P7DatabaseMetaData@fac80
16:50:03,726, DEBUG [P7DatabaseMetaData@fac80      ] [    0] getDatabaseProductName(): "Apache Derby"
16:50:03,727, DEBUG [P7Connection@18b0930f         ] [    0] isClosed(): false
16:50:03,727, DEBUG [P7Statement@6c4f9535          ] [    0] new Statement()
16:50:03,727, DEBUG [P7Connection@18b0930f         ] [    0] createStatement(): com.randomnoun.p7spy.jdbc_4_3.P7Statement@6c4f9535
16:50:03,780, DEBUG [P7Statement@6c4f9535          ] [   52] execute("CREATE TABLE wish_list    (wish_id    INT         NOT NULL GENERATED ALWAYS AS IDENTITY CONSTRAINT wish_pk PRIMARY KEY,    entry_date TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,    wish_item  VARCHAR(32) NOT NULL)"): false
16:50:03,780, DEBUG [P7Statement@6c4f9535          ] [    0] close()
16:50:03,782, DEBUG [P7Connection@18b0930f         ] [    0] isClosed(): false
16:50:03,888, DEBUG [P7PreparedStatement@4c7a078   ] [    0] new PreparedStatement()
16:50:03,888, DEBUG [P7Connection@18b0930f         ] [  106] prepareStatement("INSERT INTO wish_list ( wish_item ) VALUES ( ? )"): com.randomnoun.p7spy.jdbc_4_3.P7PreparedStatement@4c7a078
16:50:03,895, DEBUG [P7PreparedStatement@4c7a078   ] [    1] setString(1, "thing")
16:50:03,912, DEBUG [P7PreparedStatement@4c7a078   ] [   17] executeUpdate(): 1
16:50:03,912, DEBUG [P7PreparedStatement@4c7a078   ] [    0] close()
16:50:03,914, DEBUG [P7Connection@18b0930f         ] [    0] isClosed(): false
16:50:03,933, DEBUG [P7PreparedStatement@367795c7  ] [    0] new PreparedStatement()
16:50:03,933, DEBUG [P7Connection@18b0930f         ] [   19] prepareStatement("SELECT wish_item FROM wish_list WHERE wish_item = ?"): com.randomnoun.p7spy.jdbc_4_3.P7PreparedStatement@367795c7
16:50:03,933, DEBUG [P7PreparedStatement@367795c7  ] [    0] setString(1, "thing")
16:50:04,009, DEBUG [P7ResultSet@654c1a54          ] [    0] new ResultSet()
16:50:04,009, DEBUG [P7PreparedStatement@367795c7  ] [   75] executeQuery(): com.randomnoun.p7spy.jdbc_4_3.P7ResultSet@654c1a54
16:50:04,010, DEBUG [P7ResultSet@654c1a54          ] [    1] next(): true
16:50:04,025, DEBUG [P7ResultSetMetaData@19f21b6b  ] [    0] new ResultSetMetaData()
16:50:04,026, DEBUG [P7ResultSet@654c1a54          ] [   15] getMetaData(): com.randomnoun.p7spy.jdbc_4_3.P7ResultSetMetaData@19f21b6b
16:50:04,026, DEBUG [P7ResultSetMetaData@19f21b6b  ] [    0] getColumnCount(): 1
16:50:04,026, DEBUG [P7ResultSetMetaData@19f21b6b  ] [    0] getColumnLabel(1): "WISH_ITEM"
16:50:04,027, DEBUG [P7ResultSet@654c1a54          ] [    0] getObject(1): "thing"
16:50:04,027, DEBUG [P7ResultSet@654c1a54          ] [    0] next(): false
16:50:04,027, DEBUG [P7ResultSet@654c1a54          ] [    0] close()
16:50:04,027, DEBUG [P7PreparedStatement@367795c7  ] [    0] close()
```

## Releases

There were some 0.x releases before 2.0.0, but they used ant rather than maven as the build tool, and aren't in the maven central repository. 

The 0.x versions supported JDK 1.4 and 1.6 , and had a driver class name of `net.sf.p7spy.P7SpyDriver`
 
Version 2.0.0 currently requires JDK 11 and have a driver class name of `com.randomnoun.p7spy.P7SpyDriver`

## Licensing

p7spy is licensed under the BSD 2-clause license.

