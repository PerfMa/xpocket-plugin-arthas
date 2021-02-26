package com.perfma.xpocket.plugin.arthas;

import com.perfma.xlab.xpocket.spi.AbstractXPocketPlugin;
import com.perfma.xlab.xpocket.spi.context.SessionContext;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.apache.commons.net.telnet.TelnetClient;
import org.apache.commons.net.telnet.TelnetNotificationHandler;
import com.perfma.xlab.xpocket.spi.process.XPocketProcess;
import com.perfma.xlab.xpocket.spi.process.XPocketProcessAction;
import com.sun.tools.attach.AgentLoadException;

/**
 *
 * @author gongyu <yin.tong@perfma.com>
 */
public class ArthasPlugin extends AbstractXPocketPlugin implements Runnable, TelnetNotificationHandler {

    private static final String LOGO = "     _      ____    _____   _   _      _      ____  \n" +
                                       "    / \\    |  _ \\  |_   _| | | | |    / \\    / ___| \n" +
                                       "   / _ \\   | |_) |   | |   | |_| |   / _ \\   \\___ \\ \n" +
                                       "  / ___ \\  |  _ <    | |   |  _  |  / ___ \\   ___) |\n" +
                                       " /_/   \\_\\ |_| \\_\\   |_|   |_| |_| /_/   \\_\\ |____/ \n";
    
    private static final String USER_HOME = System.getProperty("user.home");
    
    private static final String path = USER_HOME + File.separator + ".xpocket" 
            + File.separator + ".arthas" + File.separator;
    
    private static final String[] files = {"arthas-agent.jar","arthas-core.jar",
        "arthas-spy.jar","logback.xml","async-profiler/libasyncProfiler-linux-arm.so",
        "async-profiler/libasyncProfiler-linux-x64.so",
        "async-profiler/libasyncProfiler-mac-x64.so"};

    private static final byte CTRL_C = 0x03;
    
    private final TelnetClient telnet = new TelnetClient();

    private boolean attachStatus = false;
    
    private int pid = -1;
    
    private SessionContext context;

    private Pattern pattern = Pattern.compile("\\[arthas@[0-9]*\\]\\$");

    private XPocketProcess process;

    @Override
    public void init(XPocketProcess process) {
        try {
            File file = new File(path + "async-profiler");
            
            if(file.exists()) {
                return;
            }
            
            file.mkdirs();
            
            for(String f : files) {
                InputStream is = ArthasPlugin.class.getClassLoader().getResourceAsStream(".arthas/lib/" + f);
                Path targetFile = new File(path + f).toPath();
                Files.copy(is, targetFile);
                is.close();
            }
            
        } catch (Throwable ex) {
            ex.printStackTrace();
        }
    }
    
    public String details(String cmd) {
        if("attach".equals(cmd)) {
            return "DESCRIPTION : \n     attach [pid] ";
        }
        
        return null;
    }
    
    public boolean isAvaibleNow(String cmd) {
        if(attachStatus) {
            return !"attach".equals(cmd);
        } else {
            return "attach".equals(cmd);
        }
    }

    public void invoke(XPocketProcess process) throws Throwable {
        this.process = process;
        String cmd = process.getCmd();
        String[] args = process.getArgs();
        
        process.register(new XPocketProcessAction(){
            @Override
            public void userInput(String input) throws Throwable {}

            @Override
            public void interrupt() throws Throwable {
                telnet.getOutputStream().write(CTRL_C);
                telnet.getOutputStream().flush();
            }
            
        });
        

        switch (cmd) {
            case "attach":
                if (!attachStatus) {
                    attach(args[0]);
                } else {
                    process.end();
                }
                break;
            case "detach":
                telnet.disconnect();
                attachOff();
                process.end();
                break;
            case "stop":
                if (attachStatus) {
                    telnet.getOutputStream().write(handleCmd(cmd, args));
                    telnet.getOutputStream().flush();
                    attachOff();
                } else {
                    process.end();
                }
                break;
            default:
                if (attachStatus) {
                    telnet.getOutputStream().write(handleCmd(cmd, args));
                    telnet.getOutputStream().flush();
                } else {
                    process.end();
                }
        }

    }

    private void startTelnet() throws IOException {
        telnet.connect("127.0.0.1", 3658);
        Thread reader = new Thread(this);
        reader.start();
    }

    private void attach(String pid) {
        VirtualMachineDescriptor virtualMachineDescriptor = null;
        for (VirtualMachineDescriptor descriptor : VirtualMachine.list()) {
            if (pid.equals(descriptor.id())) {
                virtualMachineDescriptor = descriptor;
                break;
            }
        }
        VirtualMachine virtualMachine = null;

        try {
            if (null == virtualMachineDescriptor) { // 使用 attach(String pid) 这种方式
                virtualMachine = VirtualMachine.attach(pid);
            } else {
                virtualMachine = VirtualMachine.attach(virtualMachineDescriptor);
            }

            if(virtualMachine == null) {
                process.output("Can not find Java Processor with : " + pid);
                process.end();
            }
            
            String coreJar = path + "arthas-core.jar";
            coreJar = URLEncoder.encode(coreJar, "UTF-8");
            
            String agentJar = path + "arthas-agent.jar";
            
            String loadOption = String.format("%s;;telnetPort=3658;httpPort=8563;"
                    + "ip=127.0.0.1;arthasAgent=%s;sessionTimeout=1800;"
                    + "arthasCore=%s;javaPid=%s;",coreJar,
                    URLEncoder.encode(agentJar, "UTF-8"),coreJar,pid);
            virtualMachine.loadAgent(agentJar,loadOption);
            
            startTelnet();
            attachOn(Integer.parseInt(pid));

        } catch (AgentLoadException | IOException ex) {
            process.output("Please check your jdk version both used to run "
                    + "XPocket and target processor! They must be equal!");
            process.end();
        } catch (Throwable ex) {
            process.output(ex.getClass().getName() + ":" + ex.getMessage());
            process.end();
        }
    }

    @Override
    public void run() {
        InputStream instr = telnet.getInputStream();

        try {
            int ret_read = 0, index = 0;
            char[] line = new char[1024];

            LOOP:
            for (;;) {
                ret_read = instr.read();
                
                if(ret_read == -1) {
                    break;
                }
                
                if(process == null) {
                    continue;
                }
                
                switch (ret_read) {  
                    case '\n':
                        String lineStr = new String(line, 0, index);
                        if (!lineStr.trim().equalsIgnoreCase(process.getCmd())) {
                            process.output(lineStr + "\n");
                        }
                        index = 0;
                        break;
                    case '$':
                        line = put(line,index++,(char) ret_read);
                        String flag = new String(line, 0, index);
                        if (pattern.matcher(flag).matches()) {
                            process.end();
                            process = null;
                            index = 0;
                        }
                        break;
                    default:
                        line = put(line,index++,(char) ret_read);
                }

            }
        } catch (IOException e) {
            process.output("Exception while reading socket:" + e.getMessage());
        }

        try {
            telnet.disconnect();
            attachOff();
        } catch (IOException e) {
            System.out.println("Exception while closing telnet:" + e.getMessage());
        }
    }

    private char[] put(char[] b,int index,char content) {
        char[] result = b;

        if (b.length < index && b.length * 2 > index) {
            result = new char[b.length * 2];
            System.arraycopy(b, 0, result, 0, b.length);
        } else if (b.length * 2 < index) {
            result = new char[index + 1];
            System.arraycopy(b, 0, result, 0, b.length);
        }
        result[index] = content;
        return result;
    }
    
    @Override
    public void receivedNegotiation(int negotiation_code, int option_code) {

    }

    private byte[] handleCmd(String cmd, String[] args) {
        StringBuilder cmdStr = new StringBuilder(cmd).append(' ');

        if (args != null) {
            for (String arg : args) {
                cmdStr.append(arg).append(' ');
            }
        }

        cmdStr.append("\n");

        return cmdStr.toString().getBytes();
    }

    @Override
    public void destory() throws Throwable {
        if(attachStatus) {
            telnet.getOutputStream().write(handleCmd("stop", null));
            telnet.getOutputStream().flush();
        }
    }

    @Override
    public void switchOn(SessionContext context) {
        this.context = context;
        context.setPid(pid);
    }
    
    private void attachOn(int pid) {
        if(context != null) {
            attachStatus = true;
            this.pid = pid;
            context.setPid(pid);
        }
    }
    
    private void attachOff() {
        if(context != null) {
            attachStatus = false;
            pid = -1;
            context.setPid(pid);
        }
    }

    @Override
    public void printLogo(XPocketProcess process) {
        process.output(LOGO);
    }

}
