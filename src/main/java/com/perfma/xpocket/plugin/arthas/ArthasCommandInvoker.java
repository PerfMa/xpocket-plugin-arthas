package com.perfma.xpocket.plugin.arthas;

import com.perfma.xlab.xpocket.spi.command.AbstractXPocketCommand;
import com.perfma.xlab.xpocket.spi.command.CommandList;
import com.perfma.xlab.xpocket.spi.XPocketPlugin;
import com.perfma.xlab.xpocket.spi.process.XPocketProcess;

/**
 *
 * @author gongyu <yin.tong@perfma.com>
 */
@CommandList(names={"attach","keymap","sc","sm","jad","classloader","getstatic",
    "monitor","stack","thread","trace","watch","tt","jvm","perfcounter","ognl","mc",
    "redefine","dashboard","dump","heapdump","options","reset","version",
    "session","sysprop","sysenv","vmoption","logger","profiler","stop","detach"},
     usage={"attach [pid],attach a java process and start the Arthas server in localhost 3658,then connect it",
            "keymap for Arthas keyboard shortcut",
            "check the info for the classes loaded by JVM",
            "check methods info for the loaded classes",
            "decompile the specified loaded classes",
            "check the inheritance structure, urls, class loading info for the specified class; using classloader to get the url of the resource e.g. java/lang/String.class",
            "examine class’s static properties",
            "monitor method execution statistics",
            "display the stack trace for the specified class and method",
            "show java thread information",
            "trace the execution time of specified method invocation",
            "display the input/output parameter, return object, and thrown exception of specified method invocation",
            "time tunnel, record the arguments and returned value for the methods and replay",
            "show JVM information",
            "show JVM Perf Counter information",
            "execute ognl expression",
            "Memory compiler, compiles .java files into .class files in memory",
            "load external *.class files and re-define it into JVM",
            "dashboard for the system’s real-time data",
            "dump the loaded classes in byte code to the specified location",
            "dump java heap in hprof binary format, like jmap",
            "check/set Arthas global options",
            "reset all the enhanced classes. All enhanced classes will also be reset when Arthas server is closed by stop",
            "print the version for the Arthas attached to the current Java process",
            "display current session information",
            "view/modify system properties",
            "view system environment variables",
            "view/modify the vm diagnostic options.",
            "print the logger information, update the logger level",
            "use async-profiler to generate flame graph",
            "terminate the Arthas server, all Arthas sessions will be destroyed",
            "disconnect from the Arthas server,but will not destroyed the other Arthas sessions"
        })
public class ArthasCommandInvoker extends AbstractXPocketCommand {

    private ArthasPlugin plugin;
    
    @Override
    public boolean isPiped() {
        return false;
    }

    @Override
    public void init(XPocketPlugin plugin) {
        this.plugin = (ArthasPlugin)plugin;
    }

    @Override
    public boolean isAvailableNow(String cmd) {
        return plugin.isAvaibleNow(cmd);
    }

    @Override
    public void invoke(XPocketProcess process) throws Throwable {
        plugin.invoke(process);
    }

    @Override
    public String details(String cmd) {
        return plugin.details(cmd);
    }
    
    

}
