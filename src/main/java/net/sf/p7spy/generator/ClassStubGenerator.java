package net.sf.p7spy.generator;

import java.io.*;
import java.lang.reflect.*;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.log4j.MDC;

/**
 * Generates class wrapper stubs. Is currently rolled back to compile
 * in Java1.4 (no generics or autoboxing support); need to do some kind
 * of reflectiony magic at some point.
 * 
 * <p>I'm intentionally creating java source rather than using a cglib or other AOP proxy wrapper,
 * because I'd rather debug a class whose source I have available than a generated class.
 *
 * @author knoxg
 * @version $Id$
 */
public class ClassStubGenerator {

	/** An array of classes/interfaces to wrap. Any references to these classes within the stubs
	 * will also be wrapped   
	 */
	Class[] wrappedClasses;
	
	/** An array of stub classes names corresponding to the wrapped classes above */
	String[] stubClassNames;
	
	/** The classname of an object that will encode results before they are handed to the logger, and will determine
	 * if stacktraces are to be logged */
	String resultFormatter;
	
	/** If non-null, the MDC variable that contains a reference to the wrapped object performing the logging */
	String mdcObjectId;
	
	/** If non-null, the MDC variable that contains the duration of the operation (represented in msec, as a String) */
	String mdcDurationId;
	
	/** If true, will insert code to dump a stacktrace when a SQL regex is matched */
	boolean enableTrap;

    /** Given a period-separated list of components (e.g. variable references ("a.b.c") or classnames),
     *  returns the last component. For example,
     *  getLastComponent("a.b.c.Text") will return "Text".
     *
     *  <p>If component is null, this function returns null.
     *  <p>If component contains no periods, this function returns the original string.
     *
     *  @param string The string to retrieve the last component from
     */
    static public String getLastComponent(String string) {
        if (string == null) {
            return null;
        }
        if (string.indexOf('.') == -1) {
            return string;
        }
        return string.substring(string.lastIndexOf('.') + 1);
    }
    
    /**
     * An efficient search & replace routine. Replaces all instances of
     * searchString within str with replaceString.
     *
     * @param originalString The string to search
     * @param searchString The string to search for
     * @param replaceString The string to replace it with
     *
     */
    public static String replaceString(String originalString, String searchString, String replaceString) {
        if (replaceString == null) {
            return originalString;
        }

        if (searchString == null) {
            return originalString;
        }

        if (originalString == null) {
            return null;
        }

        int loc = originalString.indexOf(searchString);

        if (loc == -1) {
            return originalString;
        }

        char[] src = originalString.toCharArray();
        int n = searchString.length();
        int m = originalString.length();
        StringBuffer buf = new StringBuffer(m + replaceString.length() - n);
        int start = 0;

        do {
            if (loc > start) {
                buf.append(src, start, loc - start);
            }

            buf.append(replaceString);
            start = loc + n;
            loc = originalString.indexOf(searchString, start);
        } while (loc > 0);

        if (start < m) {
            buf.append(src, start, m - start);
        }

        return buf.toString();
    }
	
    /** Produce a string representation of a class which stubs the supplied class.
     *  The output format is a java source file stubbing the class.
     *
     * @TODO various stubbing/wrapping strategies
     * @TODO allow multiple interfaces to be supplied
     * 
     * @param aclass The class/interface to retrieve signature information for
     * @param stubClassName the fully qualified type name of the stub being created
     * @param stubType a STUB_* constant
     * 
     * @return A string representation of the class signatures.
     */
    public String getClassStub(Class aclass, String stubClassName, int stubType) 
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(baos);

        //String stubClassName = getLastComponent(aclass.getName()) + "Stub";
        if (stubClassName.indexOf(".")!=-1) {
        	out.println("package " + stubClassName.substring(0, stubClassName.lastIndexOf('.')) + ";");
        	out.println();
        }
        out.println("import org.apache.log4j.Logger;");
        if (mdcObjectId!=null || mdcDurationId!=null) {
        	out.println("import org.apache.log4j.MDC;");
        }
        if (enableTrap) {
        	/* - is now handled in P7SpyTrace class
        	out.println("import java.util.Properties;");
        	out.println("import java.util.regex.Pattern;");
        	out.println("import java.util.regex.PatternSyntaxException;");
        	out.println("import java.io.File;");
        	out.println("import java.io.InputStream;");
        	out.println("import java.io.IOException;");
        	out.println("import java.io.FileInputStream;");
        	*/
        }
        out.println();
        
        // @TODO class javadoc
        int modifierMask = ~(Modifier.ABSTRACT | Modifier.INTERFACE);
        out.print(Modifier.toString(aclass.getModifiers() & modifierMask) + " class ");        
        
        out.print(getLastComponent(stubClassName));
        if (aclass != Object.class) {
            if (aclass.isInterface()) {
            	out.print(" implements " + aclass.getName());
            } else {
            	out.print(" extends " + aclass.getName());
            }
        }

        out.println(" {");
        Method[] methods = aclass.getDeclaredMethods();
        Method method;
        Constructor[] constructors = aclass.getConstructors();
        Constructor constructor;
        Class[] params;
        Class[] exceptions;

        out.println();
        out.println("    /** Logger used to dump method invocations */");
        out.println("    private static final Logger logger = Logger.getLogger(" + stubClassName + ".class);");
        
        out.println();
        out.println("    /** Object being wrapped by this class */");
        out.println("    private " + aclass.getName() + " w;");
        
        out.println();
        out.println("    // Constructors");
        out.println("    public " + getLastComponent(stubClassName) + "(" + aclass.getName() + " wrapped" + getLastComponent(aclass.getName()) + ") {");
        out.println("        w = wrapped" + getLastComponent(aclass.getName()) + "; ");
        if (mdcObjectId!=null) {
        	out.println("        _setMDC();");
        }
        if (mdcDurationId!=null) {
        	out.println("        MDC.put(\"" + mdcDurationId + "\", \"0\");");
        }

        out.println("        logger.debug(\"new " + getLastComponent(aclass.getName()) + "()\");");
        out.println("    }");
        out.println();
        
        if (mdcObjectId!=null) {
	        out.println();
	        out.println("    // MDC method");
	        out.println("    private void _setMDC() {");
	        out.println("        MDC.put(\"" + mdcObjectId + "\", \"" + getLastComponent(stubClassName) + "@\" + Integer.toHexString(System.identityHashCode(this)));");
			out.println("    };");
        }
        if (mdcDurationId!=null) {
	        out.println();
	        out.println("    // MDC method");
	        out.println("    private void _setMDC(long duration) {");
	        out.println("        MDC.put(\"" + mdcDurationId + "\", String.valueOf(duration));");
			out.println("    };");
        }
        
        for (int i = 0; i < constructors.length; i++) {
            constructor = constructors[i];
            out.print("    " + Modifier.toString(constructor.getModifiers()) + " " + /*constructor.getName()*/ stubClassName + "(");
            params = constructor.getParameterTypes();
            for (int j = 0; j < params.length; j++) {
                out.print(shortClassName(params[j].getName()));
                out.print(" arg" + j);
                if (j < params.length - 1) {
                    out.print(", ");
                }
            }

            out.print(")");
            exceptions = constructor.getExceptionTypes();
            if (exceptions.length > 0) {
                out.print(" throws ");
                for (int j = 0; j < exceptions.length; j++) {
                    out.print(shortClassName(exceptions[j].getName()));

                    if (j < exceptions.length - 1) {
                        out.print(", ");
                    }
                }
            }
            out.println("  {");
            out.print("        w = new " + aclass.getName() + "(");
            for (int j = 0; j < params.length; j++) {
                out.print("arg" + j);
                if (j < params.length - 1) {
                    out.print(", ");
                }
            }
            out.println(");");
            out.println("        logger = Logger.getLogger(" + stubClassName + ".class);");
            out.println("        logger.debug(\"new " + getLastComponent(aclass.getName()) + "()\");");
            out.println("        return w;");
            out.println("    }");
        }

        
        out.println();
        out.println("    // Methods");
        for (int i = 0; i < methods.length; i++) {
            method = methods[i];
            out.print(getMethodStub(stubClassName, method));
        }
        
        // implement all methods defined in the inheritance graph
        out.print(getMethodStubsForInterfaces(stubClassName, aclass.getInterfaces()));

        out.println("}");
        out.flush();

        return baos.toString();
    }
    
    
    /** Return a String containing method stubs for all the supplied interfaces
     * 
     * @param stubClassName the fully-qualified name of the class which will contain the stub methods
     * @param interfaces an array of interfaces that this class has to implement
     */
    private String getMethodStubsForInterfaces(String stubClassName, Class interfaces[]) {
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	PrintWriter out = new PrintWriter(baos);
    	Method[] methods;
    	Method method;
    	
        for (int i = 0; i < interfaces.length; i++) {
        	methods = interfaces[i].getDeclaredMethods();
            for (int j = 0; j < methods.length; j++) {
                method = methods[j];
                out.println("    // from interface " + interfaces[i].getName());
                out.print(getMethodStub(stubClassName, method));
            }
            out.print(getMethodStubsForInterfaces(stubClassName, interfaces[i].getInterfaces()));
        }
        out.flush();
    	return baos.toString();
    }
    
    /** Return a type class (as returned by <tt>Class.getName()</tt>) without any leading 
     * "class" or "interface" text.
     * 
     * @param type the result of a Class.getName() call
     * 
     * @return the supplied type, with any leading 'class' or 'interface' text removed.
     */
    private static String cleanType(Class type) {
    	String result = type.toString();
    	if (result.startsWith("class ")) { result = result.substring(6); }
    	if (result.startsWith("interface ")) { result = result.substring(10); }
    	return result;
    }

    /** Return java code to autobox a primitive java type (char, byte, short etc) into an object type
     * (Character, Byte, Short etc).
     * 
     * <p>e.g. <tt>autoBox(double, "d")</tt> will return the string "<tt>new Double(d)</tt>"
     * 
     * @param clazz type to be autoboxes
     * @param variableName variable to be autoboxed
     * 
     * @return a java fragment to autobox this variable.
     */
    private static String autoBox(Class clazz, String variableName) {
    	String wrapperType = "";
    	if (clazz.equals(char.class)) { wrapperType = "Character"; } 
    	else if (clazz.equals(byte.class)) { wrapperType = "Byte"; } 
    	else if (clazz.equals(short.class)) { wrapperType = "Short"; } 
    	else if (clazz.equals(int.class)) { wrapperType = "Integer"; } 
    	else if (clazz.equals(long.class)) { wrapperType = "Long"; } 
    	else if (clazz.equals(float.class)) { wrapperType = "Float"; } 
    	else if (clazz.equals(double.class)) { wrapperType = "Double"; }
    	else if (clazz.equals(boolean.class)) { wrapperType = "Boolean"; }
    	if (wrapperType.equals("")) {
    		return variableName;
    	} else {
    		return "new " + wrapperType + "(" + variableName + ")";
    	}
    	
    }
   
    /** Invokes a no-parameter method on an object instance, returning the value.
     * Similar to using reflection, but will only throw an IllegalArgumentException
     * on failure
     * 
     * @param object object to invoke method on
     * @param methodName method to invoke
     * 
     * @return result of method invocation
     * 
     * @return IllegalArgumentException if the supplied method does not exist, or 
     *   could not be invoked.
     */
    public Object invokeMethod(Object object, String methodName) {
    	Class clazz = object.getClass();
    	Object result = null;
    	try {
	    	Method method = clazz.getMethod("methodName", new Class[] {});
	    	result = method.invoke(clazz, new Object[] {});
    	} catch (NoSuchMethodException nsme) {
    		throw (IllegalArgumentException) new IllegalArgumentException(
    			"NoSuchMethodException invoking '" + methodName + "' on a '" + clazz.getName() + "'").initCause(nsme);
    	} catch (InvocationTargetException ite) {
    		throw (IllegalArgumentException) new IllegalArgumentException(
        			"InvocationTargetException invoking '" + methodName + "' on a '" + clazz.getName() + "'").initCause(ite);
    	} catch (IllegalAccessException iae) {
    		throw (IllegalArgumentException) new IllegalArgumentException(
        			"IllegalAccessException invoking '" + methodName + "' on a '" + clazz.getName() + "'").initCause(iae);
    	}
    	return result;
    }
    
    
    /** Returns a stubbed version of a JDBC Method, as java source
     * 
     * @param stubClassName the name of the stub class in which the generated source will be held
     * @param method the method of the JDBC interface to be stubbed
     * 
     * @return a stubbed version of a JDBC Method, as java source
     */
    private String getMethodStub(String stubClassName, Method method) {
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	PrintWriter out = new PrintWriter(baos);
    	// Type[] params;
    	Class[] params;
    	Class[] exceptions;
    	
        int modifierMask = ~(Modifier.ABSTRACT | Modifier.INTERFACE);
        
        // @TODO ONLY ENABLE THIS 1.5+ JVMS. probably via reflection. arg.
        
        Class returnTypeClass = null;
        try {
        	returnTypeClass = (Class) invokeMethod(method, "getGenericReturnType");
        } catch (IllegalArgumentException iae) {
        	returnTypeClass = method.getReturnType();
        }
        
        String returnType = shortClassName(cleanType(returnTypeClass));

        
        // ignore checking for all return types for the time being
        String wrappedReturnType = null;
        for (int i=0; i<wrappedClasses.length && wrappedReturnType == null; i++) {
        	if (returnTypeClass.equals(wrappedClasses[i])) {
        		wrappedReturnType = stubClassNames[i];
        	}
        }
        
        out.print("    " + Modifier.toString(method.getModifiers() & modifierMask) + " " + returnType + " " + method.getName() + "(");
        try {
        	params = (Class[]) invokeMethod(method, "getGenericParameterTypes");
        } catch (IllegalArgumentException iae) {
        	params = method.getParameterTypes();
        }
        
        // params = method.getGenericParameterTypes();
        params = method.getParameterTypes();
        for (int j = 0; j < params.length; j++) {
        	out.print(shortClassName(cleanType(params[j])));
        	out.print(" arg" + j);
            if (j < params.length - 1) {
                out.print(", ");
            }
        }

        out.print(")");
        exceptions = method.getExceptionTypes();
        if (exceptions.length > 0) {
            out.print(" throws ");
            for (int j = 0; j < exceptions.length; j++) {
                out.print(shortClassName(exceptions[j].getName()));
                if (j < exceptions.length - 1) {
                    out.print(", ");
                }
            }
        }
        out.println("  {");

        
        String logStatement = "        String logText = \"" + method.getName() + "(\"";
        for (int j = 0; j < params.length; j++) {
        	/*
        	if (params[j].toString().equals("java.lang.String")) {
        		logStatement += " + \"'\"";
        	}
        	logStatement += " + arg" + j;
            if (params[j].toString().equals("java.lang.String")) {
            	logStatement += " + \"'\"";
        	}*/
    		logStatement += " + " + resultFormatter + ".formatResult(" + autoBox(params[j], "arg" + j)  + ")";
            if (j < params.length - 1) {
            	logStatement += " + \", \"";
            }
        }
        logStatement += " + \")\";";
        logStatement = replaceString(logStatement, "\" + \"", "");
        out.println(logStatement);
        if (mdcDurationId!=null) {
        	out.println("        long startTime = System.currentTimeMillis();");
        }
        
        // dump exception if first arg is a string and matches what we're looking for
        // boolean enableTrap = false;
        if (enableTrap) {
	        if (params.length > 0 && params[0].toString().equals("class java.lang.String")) {
	        	// enableTrap = true;
	        	// out.println("        Exception trap = (arg0 != null && arg0.matches(\".fn_is_account_av\") ? new RuntimeException(\"SQL trap triggered\") : null;");
	        	// issues with logging a real exception if that comes along; will just perform 2 logs, and the attendant indeterminism that comes with it (could chain the exceptions I suppose)
	        	out.println("        if (arg0 != null && " + resultFormatter + ".matchesArg(arg0)) { logger.debug(\"SQL trap triggered\", new RuntimeException(\"SQL trap triggered\")); }");
	        }
        }
        
        boolean hasReturnValue = !returnType.equals("void");
        if (hasReturnValue) {
        	// @TODO if not primitive, should set to null
        	out.print("        ");	
        	out.println(shortClassName(cleanType(returnTypeClass)) + " result;");
        }
        out.println("        try {");
        out.print("            ");
        if (hasReturnValue) {
        	out.print("result = ");
        }
        out.print("w." + method.getName() + "(");
        for (int j = 0; j < params.length; j++) {
            out.print("arg" + j);
            if (j < params.length - 1) {
                out.print(", ");
            }
        }
        out.println(");");
        if (wrappedReturnType!=null) {
        	out.println("        if (!(result instanceof " + wrappedReturnType + ")) {");
        	out.println("            result = new " + wrappedReturnType + "(result);");
        	out.println("        }");
        }
        out.print("        }");
        
        // catch all declared exceptions, log, and rethrow
        for (int j = 0; j < exceptions.length; j++) {
            out.println(" catch (" + shortClassName(exceptions[j].getName()) + " e" + j + ") {");
            if (mdcDurationId!=null) {
            	out.println("            _setMDC(System.currentTimeMillis() - startTime);");
            }
            if (mdcObjectId!=null) {
            	out.println("            _setMDC();");
            }
            out.println("            logger.debug(logText, e" + j + ");");
            out.println("            throw e" + j + ";");
            out.print("        }");
        }
        out.println(" catch (RuntimeException re) {");
        // @TODO should combine these
        if (mdcDurationId!=null) {
        	out.println("            _setMDC(System.currentTimeMillis() - startTime);");
        }
        if (mdcObjectId!=null) {
        	out.println("            _setMDC();");
        }
        out.println("            logger.debug(logText, re);");
        out.println("            throw re;");
        out.println("        }");
        
        if (mdcDurationId!=null) {
        	out.println("        _setMDC(System.currentTimeMillis() - startTime);");
        }
        if (mdcObjectId != null) {
    		out.println("        _setMDC();");
    	}
        		
        // if (!method.getGenericReturnType().toString().equals("void")) {
        if (!method.getReturnType().toString().equals("void")) {
        	if (resultFormatter == null) {
        		out.println("        logger.debug(logText + \": \" + result);");
        	} else {
    			out.println("        logger.debug(logText + \": \" + " + resultFormatter + ".formatResult(" + autoBox(method.getReturnType(), "result") + "));");
        	}
        	
        	out.println("        return result;");
        } else {
        	out.println("        logger.debug(logText);");
        }
        out.println("    }");
        out.println();
    	out.flush();
    	return baos.toString();
    }
    
    
    
    /** Trims the "java.lang." package name from a classname if it is present (so "java.lang.Integer"
     * will be returned as "Integer").
     * 
     * @param className classname to return short version of
     * 
     * @return the short version of the class name
     */
    public static String shortClassName(String className) {
    	if (className.startsWith("[")) {
    		className = getTypeNameString(className);
    	}
    	if (className.startsWith("java.lang.") && className.indexOf(".", 10)==-1) {
    		return className.substring(10);
    	} else {
    		return className;
    	}
    }
    
    /** Convert a java native type signature into something more java-ish 
     * (e.g. "]Ljava.lang.String;" will return "String[]"; "B" will return "byte",
     * that sort of thing). Does not handle argument types.
     * 
     * @param typeName a java native type signature
     * 
     * @return a more java-like representation
     */
    public static String getTypeNameString(String typeName) {
    	String javaType = "";
    	int arrayCount = 0;
    	if (typeName==null) { throw new NullPointerException("null typeName"); }
    	while (typeName.startsWith("[")) {
    		arrayCount++; typeName = typeName.substring(1);
    	}
    	if (typeName.equals("Z")) { javaType = "boolean"; } 
		else if (typeName.equals("B")) { javaType = "byte"; }
		else if (typeName.equals("C")) { javaType = "char"; }
		else if (typeName.equals("S")) { javaType = "short"; }
		else if (typeName.equals("I")) { javaType = "int"; }
		else if (typeName.equals("F")) { javaType = "long"; }
		else if (typeName.equals("D")) { javaType = "float"; }
		else if (typeName.equals("B")) { javaType = "double"; }
		else if (typeName.startsWith("L")) {
			if (!typeName.endsWith(";")) {
				throw new IllegalArgumentException("Illegal typeName '" + typeName + "' (expected trailing ';')");
			}
			javaType = typeName.substring(1, typeName.length()-1); 
		}
		for (int i=0; i<arrayCount; i++) {
			javaType += "[]";
		}
		return javaType;
    }
    
    public static String usage() {
		return 
		  "Usage: java " + ClassStubGenerator.class.getName() + " [options] outputDirectory\n" +
		  "where [options] are:\n" +
		  " -p packageName     the java package for the generated .java files\n" +
		  "                      (defaults to 'com.randomnoun.db.p7spy')\n";
	}
    
    public static void main (String args[]) throws Exception {
    	int argIndex = 0;
    	String targetPackage = "net.sf.p7spy.impl";

    	if (args.length < 1) {
			System.out.println(usage());
    		throw new IllegalArgumentException("Expected outputDirectory argument or options");
    	}
    	
		while (args[argIndex].startsWith("-") && argIndex < args.length) {
			if (args[argIndex].equals("-p")) {
				targetPackage = args[argIndex + 1];
			    argIndex +=2;
			}
		}
		if (args.length < argIndex + 1) {
			System.out.println(usage());
			throw new IllegalArgumentException("Expected outputDirectory");
		}
    	String outputDirectory = args[argIndex++];

    	
    	String[] classes = {
    		"CallableStatement", 
    		"Connection", 
    		"DatabaseMetaData", 
    		"PreparedStatement",
    		"Savepoint", 
    		"Statement", 
    		"ResultSet", 
    		"ResultSetMetaData"
    	};
    	Class[] sourceClasses = new Class[classes.length];
    	String[] targetClasses = new String[classes.length];
    	
    	for (int i=0; i<classes.length; i++) {
    		sourceClasses[i] = Class.forName("java.sql." + classes[i]);
    		targetClasses[i] = targetPackage + ".P7" + classes[i];
    	}

    	// @TODO could expose these through command-line options
    	ClassStubGenerator csg = new ClassStubGenerator();
    	csg.wrappedClasses = sourceClasses;
    	csg.stubClassNames = targetClasses;
    	csg.resultFormatter = "net.sf.p7spy.P7SpyTrace";
    	csg.mdcObjectId = "p7Id";
    	csg.mdcDurationId = "p7Duration";
    	csg.enableTrap = true;
    	
    	
    	// things that might conceivably have SQL in it:
    	//   Connection.prepareCall (mult)
    	//   Connection.prepareStatement (mult)
    	//   Statement.addBatch
    	//   Statement.execute (multiple)
    	//   Statement.executeQuery (multiple)
    	//   Statement.executeUpdate
    	// will do for starters
    	
    	File dir = new File(outputDirectory + "/" + replaceString(targetPackage, ".", "/"));
    	dir.mkdirs();


    	File f;
    	PrintWriter pw;
    	for (int i=0; i<classes.length; i++) {
    		f = new File(outputDirectory + "/" + replaceString(targetPackage, ".", "/") + "/P7" + classes[i] + ".java");
    		System.out.println("Generating " + f.getCanonicalPath());
    		pw = new PrintWriter(new FileOutputStream(f));
    		pw.print(csg.getClassStub(sourceClasses[i], targetClasses[i], -1));
    		pw.close();
    	}
    	
    } 
}
