package net.sf.p7spy.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import gnu.getopt.LongOpt;
import gnu.getopt.Getopt;

/**
 * Increases the build number (stored in a software registration
 * database), and writes it to a file. Also records things that are similar to a CVS revision tag, 
 * except I don't trust CVS revision tags.
 * 
 * <p>I will document this at a later point.
 * 
 * @author knoxg
 */
public class IncreaseBuildNumber {

	/** A revision marker to be used in exception stack traces. */
	public static final String _revision = "$Id$";

	/** If set to true, increases level of debug output of this program */
	public boolean verbose;

	/** The JDBC connection string to the database containing the build data */
	private String jdbcUrl;

	/** Username to connect with */
	private String username;

	/** Password to connect with */
	private String password;

	/** Product name */
	private String product;

	/** Release identifier; should be in the format &lt;majorNumber&gt;.&lt;minorNumber&gt; */ 
	private String release;

	/** Base directory that the source parameters are relative to */
	private String baseDir;

	/** Directory of the source path that this build will be retrieved from */
	private String source;

	/** Directory of the second source path that this build will be retrieved from */
	private String source2;

	/** Directory of the third source path that this build will be retrieved from */
	private String source3;

	/** Filename to write output to (null to send output to System.out) */
	private String file;

	/** A data-object class used to contain the revision metadata for a single
	 * file under CVS source control.
	 */
	public static class CvsRecord {
		
		/** Revision to use in stack traces */
		public final static String _revision = "$Id$";
		
		/** State indicating a new local file not yet added to CVS */
		public final static String STATE_NEW = "N";
		
		/** State indicating a local file that is equivalent with that in CVS */
		public final static String STATE_SAME = "=";

		/** State indicating a local file that has been modified */
		public final static String STATE_CHANGE = "*";

		/** State indicating a local file that has been partially merged and is still in conflict */
		public final static String STATE_CONFLICT = "*";
		
		/** Relative name of the file */
		String filename;
		
		/** Revision string for the last update from CVS */
		String revision;
		
		/** The timestamp of the file on disk */
		Date fileTimestamp;
		
		/** The timestamp that this file was retrieved from CVS (if not == fileTimestamp, file has not been checked in)*/
		Date timestamp;
		
		/** The timestamp at which a conflict was detected (if not == fileTimestamp, conflicts have not been resolved)*/
		Date conflict;
		
		/** Contains sticky options (for example `-kb' for a binary file). */
		String options;  //
		  
		/** contains `T' followed by a tag name, or `D' for a date, followed by a sticky tag or date */
		String tagDate;
		
		/** Pattern used to parse valid CVS Entries lines */
		Pattern cvsPattern = Pattern.compile("^/(.*)/(.*)/(.*)/(.*)/(.*)$");
		
		/** One of the STATE_* constants in this class */		
		String state = "";
		
		/** Parse an Entries line into a CvsRecord object
		 * 
		 * <p>Syntax:
		 * <pre>
		 *   /name/revision/timestamp[+conflict]/options/tagdate
	     * e.g.
	     *   /.classpath/1.18/Mon Jul  3 13:55:11 2006//
	     *   D/JavaSource////
		 * </pre>
		 * 
		 * <p>See http://developer.apple.com/opensource/cvs/cederquist/cvs_19.html 
		 * for more details
		 * 
		 * @param directory the current directory, relative to the source base
		 * @param line line of CVS output
		 */
		public CvsRecord(String directory, String line) {
			Matcher matcher = cvsPattern.matcher(line);
			if (!matcher.matches()) {
				throw new IllegalArgumentException("Invalid Entries line '" + line + "'");
			}
			DateFormat dateFormat = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy");
			dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

			// System.out.println(line);
			this.filename = directory + matcher.group(1); 
			this.revision = matcher.group(2);
			String timestamp = matcher.group(3);
			if (timestamp.indexOf("+")!=-1) {
				try {
					this.conflict = dateFormat.parse(timestamp.substring(timestamp.indexOf("+") + 1));
				} catch (ParseException pe) {
					// unparseable dates should indicate that the user has manually 
					// modified their Entries file to indicate the file has changed. 
					this.conflict = new Date(); 
				}
				try {
					this.timestamp = dateFormat.parse(timestamp.substring(0, timestamp.indexOf("+")));				
				} catch (ParseException pe) {
					this.timestamp = new Date();
				}
				
			} else {
				try {
					this.timestamp = dateFormat.parse(timestamp);
				} catch (ParseException pe) {
					this.timestamp = new Date();
				}
			}
			this.options = matcher.group(4);
			this.tagDate = matcher.group(5);
		}
		
		/** Sees whether a date is within 2 seconds of another date; this is required
		 * since the FAT filesystem doesn't record enough precision in it's
		 * timestamps, so things could appear out of sync when they're not.
		 * 
		 * @param date1 date to compare
		 * @param date2 another date to compare
		 */
		public static boolean dateEquals(Date date1, Date date2) {
			return Math.abs(date1.getTime() - date2.getTime()) < 2000;
		}
	}	
	
	/** Create a new object to increase the build number. 
	 * 
	 * @param jdbcUrl The JDBC connection string to the database containing the build data
	 * @param username Username to connect with
	 * @param password Password to connect with
	 * @param verbose If true, increases level of debug output
	 * @param product Product name
	 * @param release Release identifier; should be in the format &lt;majorNumber&gt;.&lt;minorNumber&gt;
	 * @param baseDir Base directory that the source parameters are relative to
	 * @param source Directory of the source that this build will be retrieved from 
	 * @param source2 Directory of the second source that this build will be retrieved from
	 * @param source2 Directory of the third source that this build will be retrieved from
	 * @param file If set to null, dumps the build.properties output to System.out, otherwise will
	 *   write to this file.
	 */
	public IncreaseBuildNumber(String jdbcUrl, String username, String password, boolean verbose, String product, 
	  String release, String baseDir, String source, String source2, String source3, String file) 
	{
		this.jdbcUrl = jdbcUrl;
		this.username = username;
		this.password = password;
		this.verbose = verbose;
		this.product = product;
		this.release = release;
		this.baseDir = baseDir;
		this.source = source;
		this.source2 = source2;
		this.source3 = source3;
		this.file = file;
	}

	/** Print a usage message on System.out */
	private static void usage() {
		System.out.println("Usage: " + IncreaseBuildNumber.class.getName() + " [options]");
	    System.out.println("");
	    System.out.println("where [options] are:");
	    System.out.println("-h --help           display this usage text");
	    System.out.println("-j --jdbcUrl=xxx    * JDBC to database containing build data");
	    System.out.println("-u --username=xxx   * username to connect with");
	    System.out.println("-p --password=xxx   * password to connect with");
		System.out.println("-v --verbose        increase verbosity");
	    System.out.println("-d --product=xxx    * product name");
		System.out.println("-r --release=xxx    * release ID. Should be in the form <majorNumber>.<minorNumber>\n" +
		                   "                    [.<minorSubReleaseNumber>]");
		System.out.println("-b --baseDir=xxx    * the base directory for the source directories below");		                   
		System.out.println("-s --source=xxx     if set, the root of the source for this build \n" +
		                   "                    (used to record CVS version data)");
		System.out.println("-2 --source2=xxx    allows a second source path to be specified");
		System.out.println("-3 --source3=xxx    allows a third source path to be specified");
		System.out.println("-f --file=xxx       file to write build data to. If null, writes to stdout");
	    
	    System.out.println();
	    System.out.println("All arguments marked with an asterisk are mandatory.");
	    System.out.println();
	    System.out.println("For more information, run 'javadoc " + IncreaseBuildNumber.class.getName() + "'");
	}


	/** A FileFilter which returns all directories in a CVS source tree, excluding the 
	 * "CVS" metadata directories.
	 */
	static class DirectoryFilter implements FileFilter {
		/** Accept all non-CVS directories
		 * 
		 * @see java.io.FileFilter#accept(java.io.File)
		 */
		public boolean accept(File pathname) {
			return pathname.isDirectory() && !pathname.getName().equals("CVS");
		}
	}
	
	/** Captures the CVS metadata from the supplied directory. This method adds
	 * a CvsRecord object into the 'results' list for each file found. 
	 * 
	 * @param directory directory to start metadata search from
	 * @param relativeDirectory a prefix to add to each directory, used when recursing
	 *   into subdirectories. 
	 * @param results a modifiable list of CvsRecords 
	 * 
	 * @throws IOException if an IOException occurred reading from the filesystem
	 */ 
	private void captureCvsData(File directory, String relativeDirectory, List results) throws IOException {
		// get CVS data from here
		List ignoreFiles = new ArrayList();
		File ignoreFile = new File(directory, ".cvsignore");
		if (ignoreFile.exists()) {
			BufferedReader reader = new BufferedReader(new FileReader(ignoreFile));
			String file = reader.readLine();
			while (file!=null) {
				ignoreFiles.add(file);
				file = reader.readLine();
			}
			reader.close();
		}
		
		File entriesFile = new File(directory, "CVS/Entries");
		File entriesLogFile = new File(directory, "CVS/Entries.Log");
		// should check .cvsignore file, or maybe just allow these folders
		if (!entriesFile.exists()) {
			// throw new IllegalArgumentException("Source folder '" + directory.getCanonicalPath() + "' has no CVS/Entries file");
			System.out.println("Warning: folder '" + directory.getCanonicalPath() + "' is not in CVS; skipping");
			return;
		} else if (!entriesFile.canRead()) {
			throw new IllegalArgumentException("Source folder '" + directory.getCanonicalPath() + "' has unreadable CVS/Entries file");
		}
		
		List textData = new ArrayList();
		LineNumberReader reader = new LineNumberReader(new FileReader(entriesFile));
		String line = reader.readLine(); 
		while (line != null) {
			if (line.startsWith("D")) {
				// ignore directories
			} else if (line.startsWith("/")) {
				textData.add(line);
			} else {
				throw new IllegalArgumentException("Could not parse line '" + line + "' in file '" + 
				  entriesFile.getCanonicalPath() + "'");
			}
						
			line = reader.readLine();
		}
		reader.close();
		
		// process entriesLogFile if it exists; rules for this file are documented at
		//   http://developer.apple.com/opensource/cvs/cederquist/cvs_19.html, 
		if (entriesLogFile.exists()) {
			reader = new LineNumberReader(new FileReader(entriesLogFile));
			line = reader.readLine();
			while (line != null) {
				if (line.length()<3) { continue; }
				if (line.charAt(1)!=' ') {
					// old version of CVS - should probably emit warning 
					continue;  
				}
				char action = line.charAt(0);
				line = line.substring(2);
				if (action=='A') {
					if (line.startsWith("D")) {
						// ignore directories
					} else if (line.startsWith("/")) {
						textData.add(line);
					} else {
						throw new IllegalArgumentException("Could not parse line 'A " + line + "' in file '" + 
						  entriesLogFile.getCanonicalPath() + "'");
					}
				} else if (action=='D') {
					if (line.startsWith("D")) {
						// ignore directories
					} else if (line.startsWith("/")) {
						if (textData.contains(line)) {
							textData.remove(line);
						} else {
							System.out.println("Warning: Entries.Log refers to non-existing entry '" + line + "' - ignoring");
						}
					} else {
						throw new IllegalArgumentException("Could not parse line 'D " + line + "' in file '" + 
						  entriesLogFile.getCanonicalPath() + "'");
					}
				} else {
					// silently ignore
				}
				line = reader.readLine();
			}
			reader.close();
		}
		
		// now that Entries and Entries.Log are processed, convert these into CvsRecord lines
		for (Iterator i = textData.iterator(); i.hasNext(); ) {
			line = (String) i.next();
			CvsRecord cvsRecord = new CvsRecord(relativeDirectory, line);
			if (!cvsRecord.filename.equals(".cvsignore")) {
				File file = new File(baseDir + cvsRecord.filename);
				if (!file.exists()) {
					// file may have been removed locally, but this has not been reflected in CVS
					System.out.println("Could not find file " + baseDir + cvsRecord.filename + " (cwd=" + (new File(".")).getCanonicalPath() + ")");
				}
				cvsRecord.fileTimestamp = new Date(file.lastModified());
				
				// @TODO record new files as well
				if (cvsRecord.conflict != null && CvsRecord.dateEquals(cvsRecord.fileTimestamp, cvsRecord.conflict)) {
					cvsRecord.state = CvsRecord.STATE_CONFLICT;
				} else if (!CvsRecord.dateEquals(cvsRecord.fileTimestamp, cvsRecord.timestamp)) {
					SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
					if (verbose) {
						System.out.println("Marking " + cvsRecord.filename + " as changed: ");
						System.out.println("  on disk: " + dateFormat.format(cvsRecord.fileTimestamp));
						System.out.println("  in entries: " + dateFormat.format(cvsRecord.timestamp));
					}
					cvsRecord.state = CvsRecord.STATE_CHANGE;
				} else {
					cvsRecord.state = CvsRecord.STATE_SAME;
				}
				results.add(cvsRecord);
			}
		}
		
		// what about files not in the Entries list ? Didn't I generate warnings 
		// for these at some point ?
		
		
		// should recurse
		File[] dirs = directory.listFiles(new DirectoryFilter());
		for (int i=0; i<dirs.length; i++) {
			if (!ignoreFiles.contains(dirs[i].getName())) {
				captureCvsData(dirs[i], relativeDirectory + dirs[i].getName() + "/", results);
			}
		}
	}

	
	/** Main worker method. Captures the cvs data, writes it into the MySQL database, and 
	 * creates the build.properties file 
	 */
	public String increaseBuildNumber() throws SQLException, IOException {
		// validate parameters (JDK1.4 version)
		Pattern majorMinorPattern = Pattern.compile("^([0-9]+)\\.([0-9]+)(?:\\.([0-9]+))?");
		Matcher m = majorMinorPattern.matcher(release);
		Long majorRelease, minorRelease, minorSubrelease;
		if (m.matches()) {
			majorRelease = new Long(m.group(1));
			minorRelease = new Long(m.group(2));
			if (m.groupCount()==3 && m.group(3)!=null) {
				minorSubrelease = new Long(m.group(3));
			} else {
				minorSubrelease = null;
			}
		} else {
			throw new IllegalArgumentException("Release must be in the format majorNumber.minorNumber[.subReleaseNumber]");
		}
		
		if (!jdbcUrl.startsWith("jdbc:mysql")) {
			throw new IllegalArgumentException("Only mysql database are currently supported in this task");
		}

		Connection mysqlConn;
		JdbcTemplate jt;
		try {
			Class.forName("com.mysql.jdbc.Driver");
			mysqlConn = DriverManager.getConnection(jdbcUrl, username, password);
			jt = new JdbcTemplate(new SingleConnectionDataSource(mysqlConn, true));
		} catch (ClassNotFoundException cnfe) {
			throw (IllegalStateException) new IllegalStateException("Could not load mysql driver classes").initCause(cnfe);
		}

		long lastBuild;
		if (minorSubrelease==null) {
			lastBuild = jt.queryForLong(
			  "SELECT max(buildNumber) as lastBuildNumber " +
			  "FROM build " +
			  "WHERE product = ? AND majorRelease = ? AND minorRelease = ? AND minorSubrelease IS NULL", 
			  new Object[] { product, majorRelease, minorRelease});
		} else {
			// you'd have thought that this would work for nulls as well...
			lastBuild = jt.queryForLong(
			  "SELECT max(buildNumber) as lastBuildNumber " +
			  "FROM build " +
			  "WHERE product = ? AND majorRelease = ? AND minorRelease = ? AND minorSubrelease = ?", 
			  new Object[] { product, majorRelease, minorRelease, minorSubrelease });
		}
		
		long thisBuild = lastBuild + 1;
		String machineName = "";
		try {
			java.net.InetAddress localMachine = java.net.InetAddress.getLocalHost();	
			machineName = localMachine.getHostName();
		} catch(java.net.UnknownHostException uhe) {
			// leave name blank
		}

		jt.update("INSERT INTO build (product, majorRelease, minorRelease, minorSubrelease, dateCreated, buildNumber, machine) " +
		  "VALUES (?, ?, ?, ?, ?, ?, ?)", 
		  new Object[] { product, majorRelease, minorRelease, minorSubrelease, new Date(), new Long(thisBuild), machineName });
		Long uniqueId;
		if (minorSubrelease==null) {
			uniqueId = new Long(jt.queryForLong("SELECT id " +
			  " FROM build " +
			  " WHERE product = ? AND majorRelease = ? AND minorRelease = ? AND minorSubrelease IS NULL AND buildNumber = ?",
			  new Object[] { product, majorRelease, minorRelease, new Long(thisBuild) }));
		} else {
			uniqueId = new Long(jt.queryForLong("SELECT id " +
			  " FROM build " +
			  " WHERE product = ? AND majorRelease = ? AND minorRelease = ? AND minorSubrelease = ? AND buildNumber = ?",
			  new Object[] { product, majorRelease, minorRelease, minorSubrelease, new Long(thisBuild) }));
		}
		
		/* always keep a historical record of these things
		if (lastBuild==0) {
			//System.out.println("-- new product/majorRelease/minorRelease combination");
			jt.update("INSERT INTO build (product, majorRelease, minorRelease, buildNumber) " +
			  "VALUES (?, ?, ?, ?)", 
			  new Object[] { product, majorRelease, minorRelease, new Long(thisBuild) }); 
		} else {
			//System.out.println("-- existing product/majorRelease/minorRelease combination");
			jt.update("UPDATE build " +
			  "SET buildNumber = ? " +
			  "WHERE product = ? AND majorRelease = ? and minorRelease = ?", 
			  new Object[] { new Long(thisBuild), product, majorRelease, minorRelease }); 
		}
		// System.out.println("lastBuild = " + lastBuild);
		*/

		// capture CVS data
		if (source!=null) {
			System.out.println("Capturing CVS state");
			List cvsData = new ArrayList();  // list of CvsRecords
			
			// canonicalise filepaths -- all directory separators should be "/",
			// sourceDirs should not start with "./", 
			// sourceDirs should end with "/"
			baseDir = baseDir.replace('\\', '/');
			source = source.replace('\\', '/');
			if (source2!=null) { source2 = source2.replace('\\', '/'); }
			if (source3!=null) { source3 = source3.replace('\\', '/'); }
			if (!baseDir.endsWith("/")) { baseDir += "/"; }
			if (source.startsWith("./")) { source = source.substring(2); }
			if (source2!=null && source2.startsWith("./")) { source2 = source2.substring(2); }
			if (source3!=null && source3.startsWith("./")) { source3 = source3.substring(2); }
			if (!source.endsWith("/")) { source = source + "/"; }
			if (source2!=null && !source2.endsWith("/")) { source2 = source2 + "/"; }
			if (source3!=null && !source3.endsWith("/")) { source3 = source3 + "/"; }
			
			captureCvsData(new File(baseDir + source), source, cvsData);
			if (source2!=null) {
				captureCvsData(new File(baseDir + source2), source2, cvsData);
			}
			if (source3!=null) {
				captureCvsData(new File(baseDir + source3), source3, cvsData);
			}
			for (Iterator i = cvsData.iterator(); i.hasNext(); ) {
				CvsRecord cvsRecord = (CvsRecord) i.next();
				jt.update("INSERT INTO buildFile (buildId, filename, revision, dirtyFlag) " +
				  "VALUES (?, ?, ?, ?)", 
				  new Object[] { uniqueId, cvsRecord.filename, cvsRecord.revision, cvsRecord.state });
			}
		}

		mysqlConn.close();
		Date now = new Date();

		SimpleDateFormat sdf2 = new SimpleDateFormat("yyyyMMdd-HHmmss");
		String combinedTag = product + "-" + release + "-" + sdf2.format(now) + "-B" + thisBuild; 
		
		PrintStream out;
		if (file == null) {
			out = System.out;
		} else {
			File fileObj = new File(file);
			System.out.println("Creating file '" + fileObj.getCanonicalPath() + "'");
			out = new PrintStream(new FileOutputStream(new File(file)));
		}
		
		SimpleDateFormat sdf = new SimpleDateFormat("d/MMM/yyyy HH:mm:ss '[GMT'Z']'");
		out.println("# build information automatically generated at " + sdf.format(now));
		out.println("#");
		out.println();
		out.println("build.product=" + product);
		out.println("build.majorRelease=" + majorRelease);
		out.println("build.minorRelease=" + minorRelease);
		out.println("build.minorSubrelease=" + (minorSubrelease==null ? "" : String.valueOf(minorSubrelease)));
		out.println("build.buildNumber=" + thisBuild);
		out.println("build.buildTimestamp=" + sdf2.format(now));
		out.println("build.combinedTag=" + combinedTag);
		out.close();
		if (file!=null) {
			System.out.println("New build tag: " + combinedTag);
		}
		return combinedTag;
	}

	/** Handle illegal command-line arguments. Displays the supplied text on 
	 * System.out, then displays the usage text.
	 * 
	 * @param text Error message to display
	 */	
	private static void fail(String text) {
		System.out.println(text);
		System.out.println();
		usage();
	}	
	
	/** Main method; see class javadocs for details */
	public static void main(String args[]) {
		
		int c;
		LongOpt[] longOpts = new LongOpt[12];
		longOpts[0] = new LongOpt("help", LongOpt.NO_ARGUMENT, null, 'h');
		longOpts[1] = new LongOpt("jdbcUrl", LongOpt.REQUIRED_ARGUMENT, null, 'j');
		longOpts[2] = new LongOpt("username", LongOpt.REQUIRED_ARGUMENT, null, 'u');
		longOpts[3] = new LongOpt("password", LongOpt.REQUIRED_ARGUMENT, null, 'p');
		longOpts[4] = new LongOpt("verbose", LongOpt.NO_ARGUMENT, null, 'v');
		longOpts[5] = new LongOpt("product", LongOpt.REQUIRED_ARGUMENT, null, 'd');
		longOpts[6] = new LongOpt("release", LongOpt.REQUIRED_ARGUMENT, null, 'r');
		longOpts[7] = new LongOpt("baseDir", LongOpt.REQUIRED_ARGUMENT, null, 'b');
		longOpts[8] = new LongOpt("source", LongOpt.REQUIRED_ARGUMENT, null, 's');
		longOpts[9] = new LongOpt("source2", LongOpt.REQUIRED_ARGUMENT, null, '2');
		longOpts[10] = new LongOpt("source3", LongOpt.REQUIRED_ARGUMENT, null, '3');
		longOpts[11] = new LongOpt("file", LongOpt.REQUIRED_ARGUMENT, null, 'f');
		
		
		Getopt g = new Getopt("IncreaseBuildNumber", args, "", longOpts);
		g.setOpterr(false);
        
        String jdbcUrl=null, username=null, password=null;
        String product=null, release=null, file=null;
        String baseDir = null, source = null, source2 = null, source3=null;
        boolean verbose = false;
		while ((c = g.getopt()) != -1) {
			switch(c) {
				case 'h': usage(); System.exit(0); break;
				case 'j': jdbcUrl = g.getOptarg(); break;
				case 'u': username = g.getOptarg(); break;
				case 'p': password = g.getOptarg(); break;
				case 'd': product = g.getOptarg(); break;
				case 'r': release = g.getOptarg(); break;
				case 'b': baseDir = g.getOptarg(); break;
				case 's': source = g.getOptarg(); break;
				case '2': source2 = g.getOptarg(); break;
				case '3': source3 = g.getOptarg(); break;
				case 'f': file = g.getOptarg(); break;
				case 'v': verbose = true; break;
			}
		}
		
		// check mandatory options
		if (jdbcUrl==null) { fail("Missing jdbcUrl parameter"); System.exit(1); }
		if (username==null) { fail("Missing username parameter"); System.exit(1); }
		if (password==null) { fail("Missing password parameter"); System.exit(1); }
		if (product==null) { fail("Missing product parameter"); System.exit(1); }
		if (release==null) { fail("Missing release parameter"); System.exit(1); }
		if ((source!=null || source2!=null || source3!=null) && baseDir==null) {
			fail("Missing baseDir parameter"); System.exit(1); 
		}

		try {
			IncreaseBuildNumber ibn = new IncreaseBuildNumber(jdbcUrl, username, password, verbose, product, release, baseDir, source, source2, source3, file);
			ibn.increaseBuildNumber();
		} catch (Exception e) {
			// returncode = 1 on failure
			e.printStackTrace();
			System.exit(1);
		}
		
		// returncode = 0 on success
		System.exit(0);
		
	}

}
