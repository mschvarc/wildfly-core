package org.jboss.as.test.integration.management.cli;

import org.apache.commons.lang3.StringUtils;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


/**
 * @author Martin Schvarcbacher
 */
@RunWith(WildflyTestRunner.class)
public class CliAliasTestCase {

    @Before
    public void setup()
    {
        System.setProperty("aesh.terminal", "org.jboss.aesh.terminal.TestTerminal");
    }



    @Test
    public void testWorkFlowEchoCommand() throws Exception {
        final String ALIAS_NAME = "read_undertow";
        final String ALIAS_COMMAND = "test123";


        /*cli.executeInteractive("alias " + ALIAS_NAME+ "=" + ALIAS_COMMAND);*/
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--connect")
                .addCliArgument("--controller=" + TestSuiteEnvironment.getServerAddress() + ":" + (TestSuiteEnvironment.getServerPort()))
                .addCliArgument("--echo-command")
                //.addCliArgument("--no-local-auth")
                ;

        try{
            cli.executeInteractive("alias " + ALIAS_NAME + "=" + ALIAS_COMMAND);
            String o1 = cli.getOutput();
            cli.pushLineAndWaitForResults("");
            String o2 = cli.getOutput();
            cli.clearOutput();

            cli.pushLineAndWaitForResults("alias");
            String o3 = cli.getOutput();

            boolean closed = cli.ctrlCAndWaitForClose();

        }finally{
            cli.destroyProcess();
        }


    }


    @Test
    public void testTest2() throws Exception {
        final String ALIAS_NAME = "read_undertow";
        final String ALIAS_COMMAND = "test123";

        final ByteArrayOutputStream cliOut = new ByteArrayOutputStream();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        cliOut.reset();

        ctx.connectController();
        //ctx.handle("alias aaa=bbb");
        ctx.handle("alias");

        String out = cliOut.toString();


    }

}


