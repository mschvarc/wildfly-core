/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.test.integration.management.cli;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

import java.io.*;
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

    @Rule
    public TemporaryFolder tempUserHome = new TemporaryFolder();
    

    protected static void ensureRemoved(File f) {
        if (f.exists()) {
            if (!f.delete()) {
                fail("Failed to delete " + f.getAbsolutePath());
            }
        }
    }

    @AfterClass
    public static void cleanUpconfig() {
        ensureRemoved(TMP_AESH_ALIAS);
    }


    @BeforeClass
    public static void setupConfig() {
        ensureRemoved(TMP_AESH_RC);
        ensureRemoved(TMP_AESH_ALIAS);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(TMP_AESH_RC))) {
            writer.write("aliasFile=" + TMP_AESH_ALIAS);
            writer.newLine();
            writer.write("persistAlias=false");
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


    @Test
    public void testTestCase() throws Exception {
        final String VAR1_NAME = "variable_1";
        final String VAR1_VALUE = "value_1";

        final ByteArrayOutputStream cliOut = new ByteArrayOutputStream();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        ctx.connectController();

        ctx.handle("alias " + VAR1_NAME + "=" + VAR1_VALUE);
        ctx.handle("alias ");
        String result = cliOut.toString();
        cliOut.reset();
    }


    /**
     * Tests the alias command for the following naming pattern: [a-zA-Z0-9_]+
     *
     * @throws Exception
     */
    @Test
    public void testValidAliasCommandInteractive() throws Exception {
        final String VALID_ALIAS_NAME = "TMP123_DEBUG456__ALIAS789__";
        final String VALID_ALIAS_COMMAND = "'/subsystem=undertow:read-resource'";

        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("-Daesh.terminal=org.jboss.aesh.terminal.TestTerminal")
                .addJavaOption("-Duser.home="+ tempUserHome.toString());
        try {
            cli.executeInteractive();
            cli.pushLineAndWaitForResults("alias");
            assertFalse(cli.getOutput().contains(VALID_ALIAS_NAME));

            cli.pushLineAndWaitForResults("alias " + VALID_ALIAS_NAME + "=" + VALID_ALIAS_COMMAND);
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
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }

    /**
     * Tests for alias command containing invalid symbols in the name
     * NOTE: if this test fails in the future, see:
     * https://issues.jboss.org/browse/JBEAP-5009
     * https://issues.jboss.org/browse/JBEAP-4938
     *
     * @throws Exception
     */
    @Test
    public void testInvalidAliasCommandInteractive() throws Exception {
        final String INVALID_ALIAS_NAME = "OK_ALIAS";//"TMP-DEBUG123-INVALID456-ALIAS789";
        final String INVALID_ALIAS_COMMAND = "'/class=notfound:read-invalid-command'";
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("-Daesh.terminal=org.jboss.aesh.terminal.TestTerminal")
                .addJavaOption("-Duser.home="+ tempUserHome.toString());
        try {
            cli.executeInteractive();
            String dbg1 = cli.getOutput();
            cli.pushLineAndWaitForResults("alias"); //check if alias files does not contain test alias already
            assertFalse(cli.getOutput().contains(INVALID_ALIAS_NAME));
            cli.pushLineAndWaitForResults("alias " + INVALID_ALIAS_NAME + "=" + INVALID_ALIAS_COMMAND);
            cli.clearOutput();
            cli.pushLineAndWaitForResults("alias");
            String allAliases = cli.getOutput().replaceAll("\r", "");
            assertFalse(allAliases.contains(INVALID_ALIAS_NAME));
            assertFalse(allAliases.contains(INVALID_ALIAS_COMMAND));
            assertTrue(cli.ctrlCAndWaitForClose());
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }
}
