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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.junit.*;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.jboss.as.test.shared.TestSuiteEnvironment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author jdenise@redhat.com
 * @author Martin Schvarcbacher
 */
@RunWith(WildflyTestRunner.class)
public class CliConfigTestCase {

    private static final String SERVER_ALIAS = "Test_Suite_Server_Name";
    private static final int INVALID_PORT = TestSuiteEnvironment.getServerPort() - 2;
    private File TMP_JBOSS_CLI_FILE;

    @Before
    public void createEmptyJbossConfig() throws IOException {
        TMP_JBOSS_CLI_FILE = File.createTempFile("tmp-jboss-cli", ".xml");
    }

    /*
    @After
    public void cleanupJbossConfig() {
        ensureRemoved(TMP_JBOSS_CLI_FILE);
    }*/

    private void setupAliasConfig(Protocols defaultControllerProtocol, Protocols aliasProtocol, Integer aliasPort) {
        setupAliasConfig(defaultControllerProtocol, INVALID_PORT, aliasProtocol, aliasPort);
    }

        private void setupAliasConfig(Protocols defaultControllerProtocol, int defaultControllerPort, Protocols aliasProtocol, Integer aliasPort) {

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(TMP_JBOSS_CLI_FILE), "UTF-8"))) {
            writer.write("<?xml version='1.0' encoding='UTF-8'?>\n");
            writer.write("<jboss-cli xmlns=\"urn:jboss:cli:2.0\">\n");
            //HTTPS-REMOTING used as an invalid setting to be overridden
            writer.write("<default-protocol  use-legacy-override=\"true\">"+ Protocols.HTTPS_REMOTING+"</default-protocol>\n");
            writer.write("<default-controller>\n");
            writer.write("<protocol>" + defaultControllerProtocol + "</protocol>\n");
            writer.write("<host>"+TestSuiteEnvironment.getServerAddress()+"</host>\n");
            writer.write("<port>" + defaultControllerPort + "</port>\n");
            writer.write("</default-controller>\n");

            writer.write("<controllers>\n");
            writer.write("<controller name=\"" + SERVER_ALIAS + "\">\n");
            if (aliasProtocol != null) {
                writer.write("<protocol>" + aliasProtocol + "</protocol>\n");
            }
            writer.write("<host>" + TestSuiteEnvironment.getServerAddress() + "</host>\n");
            if (aliasPort != null) {
                writer.write("<port>" + aliasPort + "</port>\n");
            }
            writer.write("</controller>\n");

            writer.write("</controllers>\n");
            writer.write("</jboss-cli>\n");
        } catch (IOException e) {
            fail(e.getLocalizedMessage());
        }
    }

    @Test
    public void testEchoCommand() throws Exception {
        File f = createConfigFile(true);
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addCliArgument("--command=version");
        final String result = cli.executeNonInteractive();
        assertNotNull(result);
        assertTrue(result, result.contains("[disconnected /] version"));
    }

    @Test
    public void testNoEchoCommand() throws Exception {
        File f = createConfigFile(false);
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addCliArgument("--command=version");
        final String result = cli.executeNonInteractive();
        assertNotNull(result);
        assertFalse(result, result.contains("[disconnected /] version"));
    }

    @Test
    public void testWorkFlowEchoCommand() throws Exception {
        File f = createConfigFile(true);
        File script = createScript();
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addCliArgument("--file=" + script.getAbsolutePath())
                .addCliArgument("--controller=" +
                        TestSuiteEnvironment.getServerAddress() + ":" +
                        TestSuiteEnvironment.getServerPort())
                .addCliArgument("--connect");
        final String result = cli.executeNonInteractive();
        assertNotNull(result);
        assertTrue(result, result.contains(":read-attribute(name=foo)"));
        assertTrue(result, result.contains("/system-property=catch:add(value=bar)"));
        assertTrue(result, result.contains("/system-property=finally:add(value=bar)"));
        assertTrue(result, result.contains("/system-property=finally2:add(value=bar)"));
        assertTrue(result, result.contains("if (outcome == success) of /system-property=catch:read-attribute(name=value)"));
        assertTrue(result, result.contains("set prop=Catch\\ block\\ was\\ executed"));
        assertTrue(result, result.contains("/system-property=finally:write-attribute(name=value, value=if)"));

        assertFalse(result, result.contains("/system-property=catch2:add(value=bar)"));
        assertFalse(result, result.contains("set prop=Catch\\ block\\ wasn\\'t\\ executed"));
        assertFalse(result, result.contains("/system-property=finally:write-attribute(name=value, value=else)"));

        assertTrue(result, result.contains("/system-property=catch:remove()"));
        assertTrue(result, result.contains("/system-property=finally:remove()"));
        assertTrue(result, result.contains("/system-property=finally2:remove()"));
    }

    private static File createScript() {
        File f = new File(TestSuiteEnvironment.getTmpDir(), "test-script" +
                System.currentTimeMillis() + ".cli");
        f.deleteOnExit();
        try (Writer stream = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8)) {
            stream.write("try\n");
            stream.write("    :read-attribute(name=foo)\n");
            stream.write("catch\n");
            stream.write("    /system-property=catch:add(value=bar)\n");
            stream.write("finally\n");
            stream.write("    /system-property=finally:add(value=bar)\n");
            stream.write("end-try\n");

            stream.write("try\n");
            stream.write("    /system-property=catch:read-attribute(name=value)\n");
            stream.write("catch\n");
            stream.write("    /system-property=catch2:add(value=bar)\n");
            stream.write("finally\n");
            stream.write("    /system-property=finally2:add(value=bar)\n");
            stream.write(" end-try\n");

            stream.write("/system-property=*:read-resource\n");
            stream.write("if (outcome == success) of /system-property=catch:read-attribute(name=value)\n");
            stream.write("    set prop=Catch\\ block\\ was\\ executed\n");
            stream.write("    /system-property=finally:write-attribute(name=value, value=if)\n");
            stream.write("else\n");
            stream.write("    set prop=Catch\\ block\\ wasn\\'t\\ executed\n");
            stream.write("    /system-property=finally:write-attribute(name=value, value=else)\n");
            stream.write("end-if\n");
            stream.write("/system-property=catch:remove()\n");
            stream.write("/system-property=finally:remove()\n");
            stream.write("/system-property=finally2:remove()\n");
        } catch (IOException ex) {
            fail("Failure creating script file " + ex);
        }
        return f;
    }

    private static File createConfigFile(Boolean enable) {
        File f = new File(TestSuiteEnvironment.getTmpDir(), "test-jboss-cli" +
                System.currentTimeMillis() + ".xml");
        f.deleteOnExit();
        String namespace = "urn:jboss:cli:3.1";
        XMLOutputFactory output = XMLOutputFactory.newInstance();
        try (Writer stream = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8)) {
            XMLStreamWriter writer = output.createXMLStreamWriter(stream);
            writer.writeStartDocument();
            writer.writeStartElement("jboss-cli");
            writer.writeDefaultNamespace(namespace);
            writer.writeStartElement("echo-command");
            writer.writeCharacters(enable.toString());
            writer.writeEndElement(); //echo-command
            writer.writeEndElement(); //jboss-cli
            writer.writeEndDocument();
            writer.flush();
            writer.close();
        } catch (XMLStreamException | IOException ex) {
            fail("Failure creating config file " + ex);
        }
        return f;
    }

    //set invalid default controller configuration to ensure all settings are being loaded only from controller aliases
    /**
     * Default controller from jboss-cli.xml should be invalid
     *
     * @throws Exception
     */
    @Test
    public void testInvalidDefaultConfiguration() throws Exception {
        setupAliasConfig(Protocols.REMOTE, null, null);
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("-Djboss.cli.config=" + TMP_JBOSS_CLI_FILE.toPath())
                .addCliArgument("--connect");
        try {
            cli.executeNonInteractive();
            String output = cli.getOutput();
            assertDisconnected(output);
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }


    //connect to controller alias with all options (protocol, hostname, port) specified
    /**
     * Tests connection to a controller aliased in jboss-cli.xml using --controller, with all options specified
     */
    @Test
    public void testConnectToAliasedControllerImplicit() throws Exception {
        setupAliasConfig(Protocols.HTTP_REMOTING, Protocols.HTTP_REMOTING, null);
        CliProcessWrapper cli = getTestCliProcessWrapper(true);
        try {
            cli.executeNonInteractive();
            String output = cli.getOutput();
            assertConnected(output);
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }

    /**
     * Tests connection to a controller aliased in jboss-cli.xml using --controller, with all options specified
     */
    @Test
    public void testConnectToAliasedController() throws Exception {
        setupAliasConfig(Protocols.HTTPS_REMOTING, Protocols.HTTP_REMOTING, TestSuiteEnvironment.getServerPort());
        CliProcessWrapper cli = getTestCliProcessWrapper(true);
        try {
            cli.executeNonInteractive();
            String output = cli.getOutput();
            assertConnected(output);
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }

    /**
     * Tests connection to alias without specifying the port (derived from protocol)
     */
    @Test
    public void testConnectAliasImplicitSettings() throws Exception {
        setupAliasConfig(Protocols.HTTPS_REMOTING,  Protocols.HTTP_REMOTING, null);
        CliProcessWrapper cli = getTestCliProcessWrapper(true);
        try {
            cli.executeNonInteractive();
            String output = cli.getOutput();
            assertConnected(output);
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }

    /**
     *  protocol specified in <default-controller> overrides <default-protocol> when calling --connect without --controller
     */
    @Test
    public void testProtocolOverriding() throws Exception {
        //setupAliasConfig(Protocols defaultControllerProtocol, Protocols aliasProtocol, Integer aliasPort) {
        setupAliasConfig(Protocols.HTTP_REMOTING, TestSuiteEnvironment.getServerPort(), null, null);
        CliProcessWrapper cli = getTestCliProcessWrapper(false);
        try {
            cli.executeNonInteractive();
            String output = cli.getOutput();
            assertConnected(output);
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }

    private CliProcessWrapper getTestCliProcessWrapper(boolean connectToAlias){
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("-Djboss.cli.config=" + TMP_JBOSS_CLI_FILE.toPath())
                .addCliArgument("--connect")
                .addCliArgument("--echo-command")
                .addCliArgument("--command=:read-attribute(name=server-state)");
        if(connectToAlias){
            cli.addCliArgument("--controller=" + SERVER_ALIAS);
        }
        return cli;
    }

    private void assertDisconnected(String output){
        assertTrue(output.contains("Failed to connect to the controller"));
        assertTrue(output.contains("The connection failed"));
        assertFalse(output.contains("standalone@"));
    }

    private void assertConnected(String output){
        String expected = "@" + TestSuiteEnvironment.getServerAddress() + ":" + TestSuiteEnvironment.getServerPort();
        assertTrue(output.contains("\"outcome\" => \"success\""));
        assertTrue(output.contains(expected));
        assertFalse(output.contains("[disconnected /]"));
        assertFalse(output.contains("fail"));
    }

    /*
    private static void ensureRemoved(File f) {
        if (f.exists()) {
            if (!f.delete()) {
                fail("Failed to delete " + f.getAbsolutePath());
            }
        }
    }*/

    private enum Protocols {
        REMOTE("remote"),
        HTTP_REMOTING("http-remoting"),
        HTTPS_REMOTING("https-remoting");

        private final String protocolName;

        Protocols(String protocolName) {
            this.protocolName = protocolName;
        }

        @Override
        public String toString() {
            return this.protocolName;
        }
    }
}
