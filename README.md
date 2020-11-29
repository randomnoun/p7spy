# p7spy

**p7spy**  is a thing that logs JDBC statements and queries via log4j. 

It's called p7spy because I was using p6spy back in the early 2000s and that didn't do something, although I can't remember what that something was any more. 

You can configure regular expressions that will cause full stacktraces to be logged whenever any SQL executes that matches that regex. That might be it.

## Why would anyone want to do that  ?

Because you're performing software support on an application with a few million lines of code, or that's generating SQL dynamically via AOP or something and you want to find out what the golly gosh darn heck it's doing.

## How do I use it

Put it on the CLASSPATH and then shoehorn 'p7spy' into the JDBC connection string; e.g.

    jdbc:mysql://localhost/test

becomes

    jdbc:p7spy:mysql://localhost/test


or alternatively

    dbc:oracle:thin:@localhost:1521:TEST

becomes

    jdbc:p7spy:oracle:thin:@localhost:1521:TEST

Although looking at the javadocs this thing was written back in the Java 1.6 era, so it's probably not going to work without a bit of jiggering about.

## Licensing

p7spy is licensed under the BSD 2-clause license.


