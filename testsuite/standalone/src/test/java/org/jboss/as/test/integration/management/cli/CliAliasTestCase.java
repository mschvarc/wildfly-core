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
        TMP_AESH_RC = new File(new File(TestSuiteEnvironment.getTmpDir()), ".tmp-aesh-rc");
        TMP_AESH_ALIAS = new File(new File(TestSuiteEnvironment.getTmpDir()), "tmp-aesh-alias");
    }


    @Before
    public void setup() {
        // to avoid the need to reset the terminal manually after the tests, e.g. 'stty sane'
        String property1 = System.getProperty("aesh.terminal");
        String property2 = System.getProperty("aesh.aliasFile");

        System.setProperty("aesh.terminal", "org.jboss.aesh.terminal.TestTerminal");
        System.setProperty("aesh.inputrc", TMP_AESH_RC.toPath().toString());
        System.setProperty("aesh.aliasFile", TMP_AESH_ALIAS.toPath().toString());
        System.setProperty("aesh.readinputrc", "true");
        System.setProperty("aesh.persistAlias", "false");
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
        System.setProperty("aesh.persistAlias", "false");
    }


    /**
     * Tests the alias command for the following naming pattern: [a-zA-Z0-9_]+
     * @throws Exception
     */
    @Test
    public void testValidAliasCommandInteractive() throws Exception {
        final String VALID_ALIAS_NAME = "TMP123_DEBUG456__ALIAS789__";
        final String VALID_ALIAS_COMMAND = "'/subsystem=undertow:read-resource'";

        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("-Daesh.terminal=org.jboss.aesh.terminal.TestTerminal")
                .addCliArgument("-Daesh.inputrc=" + TMP_AESH_RC.toPath().toString())
                .addJavaOption("-Daesh.terminal=org.jboss.aesh.terminal.TestTerminal")
                .addJavaOption("-Daesh.inputrc=" + TMP_AESH_RC.toPath().toString())
                .addJavaOption("-Daesh.persistAlias=false")
                ;
        try {
            cli.executeInteractive();
            cli.pushLineAndWaitForResults("alias");
            assertFalse(cli.getOutput().contains(VALID_ALIAS_NAME));
            String firstout = cli.getOutput();

            cli.pushLineAndWaitForResults("alias " + VALID_ALIAS_NAME + "=" + VALID_ALIAS_COMMAND);
            String aliasingResult = cli.getOutput();
            cli.clearOutput();

            cli.pushLineAndWaitForResults("alias");
            String allAliases = cli.getOutput().replaceAll("\r", "");
            assertTrue(allAliases.contains(VALID_ALIAS_NAME));
            assertTrue(allAliases.contains(VALID_ALIAS_COMMAND));

            cli.pushLineAndWaitForResults("unalias " + VALID_ALIAS_NAME);
            cli.clearOutput();
            cli.pushLineAndWaitForResults("alias");
            String allAliasesCleared = cli.getOutput().replaceAll("\r", "");
            assertFalse(allAliasesCleared.contains(VALID_ALIAS_NAME));
        } catch (Exception ex){
            fail(ex.getLocalizedMessage());
        }
        finally {
            cli.destroyProcess();
        }
    }

    /**
     * Tests for alias command containing invalid symbols in the name, should not set invalid alias
     * NOTE: if this test fails in the future, see:
     * https://issues.jboss.org/browse/JBEAP-5009
     * https://issues.jboss.org/browse/JBEAP-4938
     *
     * @throws Exception
     */
    @Test
    public void testInvalidAliasCommandInteractive() throws Exception {
        final String INVALID_ALIAS_NAME = "TMP-DEBUG123-INVALID456-ALIAS789";
        final String INVALID_ALIAS_COMMAND = "'/class=notfound:read-invalid-command'";

        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("-Daesh.terminal=org.jboss.aesh.terminal.TestTerminal")
                //.addCliArgument("-Daesh.inputrc=" + TMP_AESH_RC.toPath().toString())
                .addJavaOption("-Daesh.terminal=org.jboss.aesh.terminal.TestTerminal")
                //.addJavaOption("-Daesh.inputrc=" + TMP_AESH_RC.toPath().toString())
                //.addJavaOption("-Daesh.persistAlias=false")
                ;

        try {
            cli.executeInteractive();
            cli.pushLineAndWaitForResults("alias");
            String dbg = cli.getOutput();
            System.err.println(dbg);
            assertFalse(cli.getOutput().contains(INVALID_ALIAS_NAME));

            cli.pushLineAndWaitForResults("alias " + INVALID_ALIAS_NAME + "=" + INVALID_ALIAS_COMMAND);
            String db2 = cli.getOutput();
            cli.clearOutput();
            cli.pushLineAndWaitForResults("alias");
            String allAliases = cli.getOutput().replaceAll("\r", "");
            assert true;
            assertFalse(allAliases.contains(INVALID_ALIAS_NAME));
            assertFalse(allAliases.contains(INVALID_ALIAS_COMMAND));
        } catch (Exception ex){
            fail(ex.getLocalizedMessage());
            CliProcessWrapper cliCleanup = new CliProcessWrapper();
            cliCleanup.executeInteractive();
            cliCleanup.pushLineAndWaitForResults("unalias " + INVALID_ALIAS_NAME);
            cliCleanup.ctrlCAndWaitForClose();
            cliCleanup.destroyProcess();
        }
        finally {
            cli.destroyProcess();
        }
    }
}


