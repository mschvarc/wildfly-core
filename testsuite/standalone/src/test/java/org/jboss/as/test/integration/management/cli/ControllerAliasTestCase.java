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

import org.junit.Before;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;


import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;


/**
 * Tests the support of user defined controller aliases defined in jboss-cli.xml
 * @author Martin Schvarcbacher
 */
@RunWith(WildflyTestRunner.class)
public class ControllerAliasTestCase {

    private static final File TMP_JBOSS_CLI_FILE;
    private static final String SERVER_ALIAS = "Test-Suite_Server-Name";
    private static final int INVALID_PORT = TestSuiteEnvironment.getServerPort() - 2;
    static {
        TMP_JBOSS_CLI_FILE = new File(TestSuiteEnvironment.getTmpDir(), "tmp-jboss-cli.xml");
    }

    @BeforeClass
    public static void setup() {
        ensureRemoved(TMP_JBOSS_CLI_FILE);
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(TMP_JBOSS_CLI_FILE), "UTF-8"))) {
            writer.write("<?xml version='1.0' encoding='UTF-8'?>\n");
            writer.write("<jboss-cli xmlns=\"urn:jboss:cli:2.0\">\n");
            writer.write("<default-protocol  use-legacy-override=\"true\">http-remoting</default-protocol>\n");
            writer.write("<default-controller>\n");
            writer.write("<protocol>http-remoting</protocol>\n");
            writer.write("<host>localhost</host>\n");
            writer.write("<port>" + INVALID_PORT + "</port>\n");
            writer.write("</default-controller>\n");
            writer.write("<controllers>\n");

            writer.write("<controller name=\"" + SERVER_ALIAS + "\">\n");
            writer.write("<protocol>http-remoting</protocol>\n");
            writer.write("<host>" + TestSuiteEnvironment.getServerAddress() + "</host>\n");
            writer.write("<port>" + TestSuiteEnvironment.getServerPort() + "</port>\n");
            writer.write("</controller>\n");

            writer.write("</controllers>\n");
            writer.write("</jboss-cli>\n");
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
        assertServerConnected(cli.getOutput());
    }

    /**
     * Tests connection to a controller aliased in jboss-cli.xml
     * using controller=MyController
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
        assertServerConnected(cli.getOutput());
    }


    private void assertServerConnected(String output) {
        String expected = "@" + TestSuiteEnvironment.getServerAddress() + ":" + TestSuiteEnvironment.getServerPort();
        assertTrue(output.contains("\"outcome\" => \"success\""));
        assertTrue(output.contains(expected));
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
