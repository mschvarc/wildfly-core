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

import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/**
 * Tests the 'alias' and 'unalias' command in interactive mode of jboss-cli
 * @author Martin Schvarcbacher
 */
@RunWith(WildflyTestRunner.class)
public class CliAliasTestCase {

    //@Rule
    //public TemporaryFolder tempUserHome = new TemporaryFolder();
    private static Path tempUserHome;

    @Before
    public void setupTempFolder() throws IOException {
        tempUserHome = Files.createTempDirectory("jboss-cli");
    }

    @After
    public void cleanup()
    {

    }

    /**
     * Tests the alias command for the following naming pattern: [a-zA-Z0-9_]+
     *
     * @throws Exception
     */
    @Test
    public void testValidAliasCommandInteractive() throws Exception {
        final String VALID_ALIAS_NAME = "TMP123_DEBUG456__ALIASVALID789__";
        final String VALID_ALIAS_COMMAND = "'/subsystem=undertow:read-resource'";

        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("-Daesh.terminal=org.jboss.aesh.terminal.TestTerminal")
                .addJavaOption("-Duser.home="+ tempUserHome.toString())
                .addCliArgument("-Duser.home="+ tempUserHome.toString());
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
        final String INVALID_ALIAS_NAME = "TMP-DEBUG123-#INVALID456-ALIAS789";
        final String INVALID_ALIAS_COMMAND = "'/class=notfound:read-invalid-command'";
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("-Daesh.terminal=org.jboss.aesh.terminal.TestTerminal")
                .addJavaOption("-Duser.home="+ tempUserHome.toAbsolutePath().toString())
                .addCliArgument("-Duser.home="+ tempUserHome.toAbsolutePath().toString());
        try {
            cli.executeInteractive();
            cli.pushLineAndWaitForResults("alias");
            assertFalse(cli.getOutput().contains(INVALID_ALIAS_NAME));
            assertFalse(cli.getOutput().contains(INVALID_ALIAS_COMMAND));
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
