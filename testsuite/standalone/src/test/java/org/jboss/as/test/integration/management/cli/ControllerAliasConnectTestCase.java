package org.jboss.as.test.integration.management.cli;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

import static org.junit.Assert.*;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Martin Schvarcbacher
 *
 * https://issues.jboss.org/browse/JBEAP-5009 //limitations [a-zA-Z0-9_]+ only var names
 * https://issues.jboss.org/browse/JBEAP-3164
 *
 */
@RunWith(WildflyTestRunner.class)
public class ControllerAliasConnectTestCase {

    private static final File TMP_JBOSS_CLI_FILE;
    private static final String SERVER_ALIAS = "TestSuiteServer";
    private static final int INVALID_PORT = TestSuiteEnvironment.getServerPort() - 1;

    static {
        TMP_JBOSS_CLI_FILE = new File(new File(TestSuiteEnvironment.getTmpDir()), "tmp-jboss-cli.xml");
    }

    @BeforeClass
    public static void setup() {
        ensureRemoved(TMP_JBOSS_CLI_FILE);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(TMP_JBOSS_CLI_FILE))) {
            writer.write("<?xml version='1.0' encoding='UTF-8'?>");
            writer.newLine();
            writer.write("<jboss-cli xmlns=\"urn:jboss:cli:2.0\">\n");
            writer.newLine();
            writer.write("<default-protocol  use-legacy-override=\"true\">http-remoting</default-protocol> ");
            writer.newLine();
            writer.write("<default-controller>  ");
            writer.newLine();
            writer.write("<protocol>http-remoting</protocol>");
            writer.newLine();
            writer.write("<host>localhost</host>");
            writer.newLine();
            writer.write("<port>"+INVALID_PORT+"</port>");
            writer.newLine();
            writer.write("</default-controller> ");
            writer.write("<controllers>");
            writer.newLine();
            writer.write("<controller name=\"" + SERVER_ALIAS + "\">");
            writer.newLine();
            writer.write("<protocol>http-remoting</protocol>");
            writer.newLine();
            writer.write("<host>" + TestSuiteEnvironment.getServerAddress() + "</host>");
            writer.newLine();
            writer.write("<port>" + TestSuiteEnvironment.getServerPort() + "</port>");
            writer.newLine();
            writer.write("</controller>");
            writer.newLine();
            writer.write("</controllers>");
            writer.newLine();
            writer.write("</jboss-cli>");
            writer.newLine();

        } catch (IOException e) {
            fail(e.getLocalizedMessage());
        }
    }

    @AfterClass
    public static void cleanUp() {
        ensureRemoved(TMP_JBOSS_CLI_FILE);
    }



    @Before
    public void assertInvalidDefaultConfiguration() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addJavaOption("-Djboss.cli.config=" + TMP_JBOSS_CLI_FILE.toPath())
                .addCliArgument("-Djboss.cli.config=" + TMP_JBOSS_CLI_FILE.toPath())
                .addCliArgument("--connect");
        cli.executeNonInteractive();
        String output = cli.getOutput();
        assertTrue(output.contains("Failed to connect to the controller"));
        assertTrue(output.contains("The connection failed"));
    }

    /**
     * Tests connection to a controller aliased in jboss-cli.xml
     * using --controller=MyController
     * @throws Exception
     */
    @Test
    public void testConnectToAliasedControllerMinus() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("-Djboss.cli.config=" + TMP_JBOSS_CLI_FILE.toPath())
                .addCliArgument("--controller=" + SERVER_ALIAS)
                .addCliArgument("--connect")
                .addCliArgument("--echo-command")
                .addCliArgument("--commands=:read-attribute(name=server-state)");
        cli.executeNonInteractive();
        checkServerConnected(cli.getOutput());
    }

    /**
     * Tests connection to a controller aliased in jboss-cli.xml
     * with controller=MyController
     * @throws Exception
     */
    @Test
    public void testConnectToAliasedController() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("-Djboss.cli.config=" + TMP_JBOSS_CLI_FILE.toPath())
                .addCliArgument("controller=" + SERVER_ALIAS)
                .addCliArgument("--connect")
                .addCliArgument("--echo-command")
                .addCliArgument("--commands=:read-attribute(name=server-state)");
        cli.executeNonInteractive();
        checkServerConnected(cli.getOutput());
    }

    private void checkServerConnected(String output)
    {
        String expectedPrompt = "@" + TestSuiteEnvironment.getServerAddress() + ":" + TestSuiteEnvironment.getServerPort();
        assertTrue(output.contains("\"outcome\" => \"success\""));
        assertTrue(output.contains(expectedPrompt));
        assertFalse(output.contains("[disconnected /]"));
        assertFalse(output.contains("fail"));
    }

    protected static void ensureRemoved(File f) {
        if (f.exists()) {
            if (!f.delete()) {
                fail("Failed to delete " + f.getAbsolutePath());
            }
        }
    }
}
