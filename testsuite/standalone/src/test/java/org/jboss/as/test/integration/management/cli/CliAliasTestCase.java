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

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/**
 * Tests the 'alias' and 'unalias' command in interactive mode of jboss-cli
 *
 * @author Martin Schvarcbacher
 */
@RunWith(WildflyTestRunner.class)
public class CliAliasTestCase {

    private static final String VALID_ALIAS_NAME = "TMP123_DEBUG456__ALIASVALID789__";
    private static final String VALID_ALIAS_COMMAND = "'/subsystem=undertow:read-resource'";

    @Rule
    public final TemporaryFolder tempUserHome = new TemporaryFolder();


    /**
     * Tests the alias command for the following naming pattern: [a-zA-Z0-9_]+
     *
     * @throws Exception
     */
    @Test
    public void testValidAliasCommandInteractive() throws Exception {


        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("-Daesh.terminal=org.jboss.aesh.terminal.TestTerminal")
                .addJavaOption("-Duser.home=" + tempUserHome.getRoot().toPath().toString())
                .addCliArgument("-Duser.home=" + tempUserHome.getRoot().toPath().toString());
        try {
            cli.executeInteractive();
            cli.pushLineAndWaitForResults("alias");
            assertFalse(cli.getOutput().contains(VALID_ALIAS_NAME));
            assertFalse(cli.getOutput().contains(VALID_ALIAS_COMMAND));
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
     * Tests alias command containing invalid symbols in the name
     * NOTE: if this test fails in the future:
     * <a href="https://issues.jboss.org/browse/JBEAP-5009">JBEAP-5009</a>
     * <a href="https://issues.jboss.org/browse/JBEAP-4938">JBEAP-4938</a>
     *
     * @throws Exception
     */
    @Test
    public void testInvalidAliasCommandInteractive() throws Exception {
        final String INVALID_ALIAS_NAME = "TMP-*DEBUG123-#INVALID456-ALIAS789";
        final String INVALID_ALIAS_COMMAND = "'/class=notfound:read-invalid-command'";
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("-Daesh.terminal=org.jboss.aesh.terminal.TestTerminal")
                .addJavaOption("-Duser.home=" + tempUserHome.getRoot().toPath().toString())
                .addCliArgument("-Duser.home=" + tempUserHome.getRoot().toPath().toString());
        try {

            cli.executeInteractive();
            cli.pushLineAndWaitForResults("alias");
            assertFalse(cli.getOutput().contains(INVALID_ALIAS_NAME));
            assertFalse(cli.getOutput().contains(INVALID_ALIAS_COMMAND));
            cli.pushLineAndWaitForResults("alias " + INVALID_ALIAS_NAME + "=" + INVALID_ALIAS_COMMAND);
            cli.clearOutput();
            cli.pushLineAndWaitForResults("alias");
            String allAliases = cli.getOutput().replaceAll("\r", "");
            //see: https://issues.jboss.org/browse/JBEAP-5009
            assertFalse(allAliases.contains(INVALID_ALIAS_NAME));
            assertFalse(allAliases.contains(INVALID_ALIAS_COMMAND));
            assertTrue(cli.ctrlCAndWaitForClose());
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }

    @Test
    public void testManuallyAddedAlias() throws Exception {
        final File aliasFile = tempUserHome.newFile(".aesh_aliases");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(aliasFile), "UTF-8"))) {
            writer.write("alias " + VALID_ALIAS_NAME + "=" + VALID_ALIAS_COMMAND);
            writer.newLine();
        } catch (IOException ex) {
            fail(ex.getLocalizedMessage());
        }
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("-Daesh.terminal=org.jboss.aesh.terminal.TestTerminal")
                .addJavaOption("-Duser.home=" + tempUserHome.getRoot().toPath().toString())
                .addCliArgument("-Duser.home=" + tempUserHome.getRoot().toPath().toString());
        try {
            cli.executeInteractive();
            cli.pushLineAndWaitForResults("alias");
            String allAliases = cli.getOutput().replaceAll("\r", "");
            assertTrue(allAliases.contains("alias "));
            assertTrue(allAliases.contains(VALID_ALIAS_NAME));
            assertTrue(allAliases.contains(VALID_ALIAS_COMMAND));
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }

    @Test
    public void testAliasPersistence() throws Exception {
        final File aliasFile = tempUserHome.newFile(".aesh_aliases");
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("-Daesh.terminal=org.jboss.aesh.terminal.TestTerminal")
                .addJavaOption("-Duser.home=" + tempUserHome.getRoot().toPath().toString())
                .addCliArgument("-Duser.home=" + tempUserHome.getRoot().toPath().toString());
        try {
            cli.executeInteractive();
            cli.pushLineAndWaitForResults("alias " + VALID_ALIAS_NAME + "=" + VALID_ALIAS_COMMAND);
            cli.ctrlCAndWaitForClose();
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
        List<String> aliasesInFile = Files.readAllLines(aliasFile.toPath(), Charset.defaultCharset());
        boolean found = false;
        for (String line : aliasesInFile) {
            if (line.contains("alias ") && line.contains(VALID_ALIAS_NAME)  && line.contains(VALID_ALIAS_COMMAND)) {
                found = true;
                break;
            }
        }
        assertTrue("Alias was not saved to .aesh_aliases", found);
    }
}
