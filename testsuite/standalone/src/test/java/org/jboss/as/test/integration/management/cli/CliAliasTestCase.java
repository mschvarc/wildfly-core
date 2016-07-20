package org.jboss.as.test.integration.management.cli;


import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/**
 * @author Martin Schvarcbacher
 */
@RunWith(WildflyTestRunner.class)
public class CliAliasTestCase {

    private static final File TMP_AESH_RC;
    private static final File TMP_AESH_ALIAS;

    static {
        TMP_AESH_RC = new File(new File(TestSuiteEnvironment.getTmpDir()), "tmp-aesh-rc");
        TMP_AESH_ALIAS = new File(new File(TestSuiteEnvironment.getTmpDir()), "tmp-aesh-alias");
    }


    @Before
    public void setup() {
        // to avoid the need to reset the terminal manually after the tests, e.g. 'stty sane'
        String property1 = System.getProperty("aesh.terminal");
        String property2 = System.getProperty("aesh.aliasFile");

        System.setProperty("aesh.terminal", "org.jboss.aesh.terminal.TestTerminal");
    }


    protected static void ensureRemoved(File f) {
        if (f.exists()) {
            if (!f.delete()) {
                fail("Failed to delete " + f.getAbsolutePath());
            }
        }
    }

    @AfterClass
    public static void cleanUpconfig() {
        ensureRemoved(TMP_AESH_RC);
        ensureRemoved(TMP_AESH_ALIAS);
    }


    @BeforeClass
    public static void setupConfig() {
        ensureRemoved(TMP_AESH_RC);
        ensureRemoved(TMP_AESH_ALIAS);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(TMP_AESH_RC))) {
            writer.write("aliasFile=" + TMP_AESH_ALIAS);
            writer.newLine();

            Files.createFile(TMP_AESH_ALIAS.toPath());
        } catch (IOException e) {
            fail(e.getLocalizedMessage());
        }

        // to avoid the need to reset the terminal manually after the tests, e.g. 'stty sane'
        System.setProperty("aesh.terminal", "org.jboss.aesh.terminal.TestTerminal");
        System.setProperty("aesh.inputrc", TMP_AESH_RC.toPath().toString());
        System.setProperty("aesh.aliasFile", TMP_AESH_ALIAS.toPath().toString());
        System.setProperty("aesh.readinputrc", "true");
    }


    @Test
    public void testValidAliasCommandInteractive() throws Exception {
        final String VALID_ALIAS_NAME = "TMP_DEBUG_ALIAS";
        final String VALID_ALIAS_COMMAMND = "'/subsystem=undertow:read-resource'";

        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--connect")
                .addCliArgument("--controller=" + TestSuiteEnvironment.getServerAddress() + ":" + (TestSuiteEnvironment.getServerPort()))
                /*.addCliArgument("-Daesh.terminal=org.jboss.aesh.terminal.TestTerminal")
                .addCliArgument("-Daesh.inputrc=" + TMP_AESH_RC.toPath().toString())
                .addJavaOption("-Daesh.terminal=org.jboss.aesh.terminal.TestTerminal")
                .addJavaOption("-Daesh.inputrc=" + TMP_AESH_RC.toPath().toString())*/;
        try {
            cli.executeInteractive("alias");
            assertFalse(cli.getOutput().contains(VALID_ALIAS_NAME));
            assertFalse(cli.getOutput().contains(VALID_ALIAS_COMMAMND));
            String firstout = cli.getOutput();

            cli.pushLineAndWaitForResults("alias " + VALID_ALIAS_NAME + "=" + VALID_ALIAS_COMMAMND);
            String aliasingResult = cli.getOutput();
            cli.clearOutput();

            cli.pushLineAndWaitForResults("alias");
            String allAliases = cli.getOutput().replaceAll("\r", "");
            assertTrue(allAliases.contains(VALID_ALIAS_NAME));
            assertTrue(allAliases.contains(VALID_ALIAS_COMMAMND));

            cli.pushLineAndWaitForResults("unalias " + VALID_ALIAS_NAME);
            cli.clearOutput();
            cli.pushLineAndWaitForResults("alias");
            String allAliasesCleared = cli.getOutput().replaceAll("\r", "");
            assertFalse(allAliasesCleared.contains(VALID_ALIAS_NAME));

        } finally {
            cli.destroyProcess();
        }
    }

    /**
     * NOTE: should this test fail in the future, see https://issues.jboss.org/browse/JBEAP-5009 and change accordingly
     *
     * @throws Exception
     */
    @Test
    public void testInvalidAliasCommandInteractive() throws Exception {
        final String INVALID_ALIAS_NAME = "TMP_DEBUG-ALIAS"; //minus sign does not match allowed name pattern
        final String INVALID_ALIAS_COMMAMND = "'/subsystem=undertow:read-resource'";

        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--connect")
                .addCliArgument("--controller=" + TestSuiteEnvironment.getServerAddress() + ":" + (TestSuiteEnvironment.getServerPort()));
        try {
            cli.executeInteractive("alias");
            assertFalse(cli.getOutput().contains(INVALID_ALIAS_NAME));
            assertFalse(cli.getOutput().contains(INVALID_ALIAS_COMMAMND));

            cli.pushLineAndWaitForResults("alias " + INVALID_ALIAS_NAME + "=" + INVALID_ALIAS_COMMAMND);
            cli.clearOutput();
            cli.pushLineAndWaitForResults("alias");
            String allAliases = cli.getOutput().replaceAll("\r", "");
            assertFalse(allAliases.contains(INVALID_ALIAS_NAME));
            assertFalse(allAliases.contains(INVALID_ALIAS_COMMAMND));
        } finally {
            cli.destroyProcess();
        }
    }


}


