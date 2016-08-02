/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.remoting.Protocol;

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

    private static final String CONTROLLER_ALIAS_NAME = "Test_Suite_Server1_Name";
    private static final int INVALID_PORT = TestSuiteEnvironment.getServerPort() - 1;
    private File TMP_JBOSS_CLI_FILE;

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

    /**
     * Writes specified config to TMP_JBOSS_CLI_FILE for use as jboss-cli.[sh/bat] settings
     *
     * @param defaultProtocol   default-protocol header
     * @param defaultController settings for default-controller
     * @param aliasController   settings for aliased controller
     */
    private void writeJbossCliConfig(String defaultProtocol, String defaultController, String aliasController) {
        try {
            TMP_JBOSS_CLI_FILE = File.createTempFile("tmp-jboss-cli", ".xml");
        } catch (IOException e) {
            fail(e.getLocalizedMessage());
        }
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(TMP_JBOSS_CLI_FILE), "UTF-8"))) {
            writer.write("<?xml version='1.0' encoding='UTF-8'?>\n");
            writer.write("<jboss-cli xmlns=\"urn:jboss:cli:3.1\">\n");
            if (defaultProtocol != null) {
                writer.write(defaultProtocol);
            }
            if (defaultController != null) {
                writer.write(defaultController);
            }
            if (aliasController != null) {
                writer.write(aliasController);
            }
            writer.write("</jboss-cli>\n");
        } catch (IOException e) {
            fail(e.getLocalizedMessage());
        }
    }

    private void writeJbossCliConfig(String defaultProtocol, String defaultController) {
        writeJbossCliConfig(defaultProtocol, defaultController, null);
    }

    private String createDefaultProtocol(boolean useLegacyOverride, Protocol defaultProtocol) {
        StringBuilder builder = new StringBuilder();
        builder.append("<default-protocol  use-legacy-override=\"" + useLegacyOverride + "\">");
        builder.append(defaultProtocol + "</default-protocol>\n");
        return builder.toString();
    }

    private String createDefaultController(Protocol defaultControllerProtocol, Integer defaultControllerPort) {
        StringBuilder builder = new StringBuilder();
        builder.append("<default-controller>\n");
        if (defaultControllerProtocol != null) {
            builder.append("<protocol>" + defaultControllerProtocol + "</protocol>\n");
        }
        builder.append("<host>" + TestSuiteEnvironment.getServerAddress() + "</host>\n");
        if (defaultControllerPort != null) {
            builder.append("<port>" + defaultControllerPort + "</port>\n");
        }
        builder.append("</default-controller>\n");
        return builder.toString();
    }

    private String createControllerAlias(Protocol aliasControllerProtocol, Integer aliasControllerPort) {
        StringBuilder builder = new StringBuilder();
        builder.append("<controllers>\n");
        builder.append("<controller name=\"" + CONTROLLER_ALIAS_NAME + "\">\n");
        if (aliasControllerProtocol != null) {
            builder.append("<protocol>" + aliasControllerProtocol + "</protocol>\n");
        }
        builder.append("<host>" + TestSuiteEnvironment.getServerAddress() + "</host>\n");
        if (aliasControllerPort != null) {
            builder.append("<port>" + aliasControllerPort + "</port>\n");
        }
        builder.append("</controller>\n");
        builder.append("</controllers>\n");
        return builder.toString();
    }

    /**
     * Default controller from jboss-cli.xml should be invalid to ensure settings are loaded from controller alias
     */
    @Test
    public void testInvalidDefaultConfiguration() {
        writeJbossCliConfig(
                createDefaultProtocol(true, Protocol.REMOTE),
                createDefaultController(Protocol.HTTP_REMOTING, INVALID_PORT));
        CliProcessWrapper cli = getTestCliProcessWrapper(false);
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

    /**
     * Tests connection to a controller aliased in jboss-cli.xml using --controller,
     * with all options (protocol, hostname, port) specified
     */
    @Test
    public void testConnectToAliasedController() {
        writeJbossCliConfig(
                createDefaultProtocol(true, Protocol.REMOTE),
                createDefaultController(Protocol.HTTPS_REMOTING, INVALID_PORT),
                createControllerAlias(Protocol.HTTP_REMOTING, TestSuiteEnvironment.getServerPort()));
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
     * Tests if protocol specified in default-controller overrides default-protocol when calling --connect without --controller
     */
    @Test
    public void testProtocolOverridingConnected() {
        writeJbossCliConfig(
                createDefaultProtocol(true, Protocol.HTTPS_REMOTING), //invalid settings
                createDefaultController(Protocol.HTTP_REMOTING, TestSuiteEnvironment.getServerPort()));
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

    /**
     * Returns CliProcessWrapper with settings loaded from TMP_JBOSS_CLI_FILE
     *
     * @param connectToAlias connects to aliased controller if true, to default controller otherwise
     * @return configured not started  CliProcessWrapper
     */
    private CliProcessWrapper getTestCliProcessWrapper(boolean connectToAlias) {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("-Djboss.cli.config=" + TMP_JBOSS_CLI_FILE.toPath())
                .addCliArgument("--connect")
                .addCliArgument("--echo-command")
                .addCliArgument("--command=:read-attribute(name=server-state)");
        if (connectToAlias) {
            cli.addCliArgument("--controller=" + CONTROLLER_ALIAS_NAME);
        }
        return cli;
    }

    private void assertDisconnected(String output) {
        assertTrue(output.contains("Failed to connect to the controller"));
        assertTrue(output.contains("The connection failed"));
        assertFalse(output.contains("standalone@"));
    }

    private void assertConnected(String output) {
        String expected = "@" + TestSuiteEnvironment.getServerAddress() + ":" + TestSuiteEnvironment.getServerPort();
        assertTrue(output.contains("\"outcome\" => \"success\""));
        assertTrue(output.contains(expected));
        assertFalse(output.contains("[disconnected /]"));
        assertFalse(output.contains("fail"));
    }

    /**
     * Test for use-legacy-override=true, no connection protocol specified and port set to 9999
     * Missing options should not be loaded from default-controller
     */
    @Test
    public void testUseLegacyOverrideTrue() {
        writeJbossCliConfig(
                createDefaultProtocol(true, Protocol.HTTP_REMOTING),
                createDefaultController(Protocol.HTTPS_REMOTING, INVALID_PORT),
                createControllerAlias(null, 9999));
        CliProcessWrapper cli = getTestCliProcessWrapper(true);
        try {
            cli.executeNonInteractive();
            String output = cli.getOutput();
            assertTrue(output.contains(":" + 9999));
            assertTrue(output.contains("remoting://") || output.contains("remote://"));
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }

    /**
     * Test for use-legacy-override=false, ALIAS: no connection protocol specified and port set to 9999
     * Missing options should not be loaded from default-controller
     */
    @Test
    public void testUseLegacyOverrideFalse() {
        Protocol expectedProtocol = Protocol.HTTPS_REMOTING;
        writeJbossCliConfig(
                createDefaultProtocol(false, expectedProtocol),
                createDefaultController(Protocol.HTTP_REMOTING, INVALID_PORT),
                createControllerAlias(null, 9999));
        CliProcessWrapper cli = getTestCliProcessWrapper(true);
        try {
            cli.executeNonInteractive();
            String output = cli.getOutput();
            assertTrue(output.contains(":" + 9999));
            assertTrue(output.contains(expectedProtocol + "://"));
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }

    /**
     * Tests behavior of default-controller without specified protocol and using non-standard port
     * Protocol should be taken from default-protocol
     */
    @Test
    public void testDefaultControllerNoExplicitPort() {
        Protocol expectedProtocol = Protocol.HTTPS_REMOTING;
        writeJbossCliConfig(
                createDefaultProtocol(true, expectedProtocol),
                createDefaultController(null, INVALID_PORT));
        CliProcessWrapper cli = getTestCliProcessWrapper(false);
        try {
            cli.executeNonInteractive();
            String output = cli.getOutput();
            assertTrue(output.contains(":" + INVALID_PORT));
            assertTrue(output.contains(expectedProtocol + "://"));
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }

    /**
     * Tests if default-protocol is overridden by protocol defined in default-controller
     */
    @Test
    public void testDefaultProtocolOverriding() {
        Protocol expectedProtocol = Protocol.HTTPS_REMOTING;
        writeJbossCliConfig(
                createDefaultProtocol(true, Protocol.REMOTE),
                createDefaultController(expectedProtocol, INVALID_PORT));
        CliProcessWrapper cli = getTestCliProcessWrapper(false);
        try {
            cli.executeNonInteractive();
            String output = cli.getOutput();
            assertTrue(output.contains(":" + INVALID_PORT));
            assertTrue(output.contains(expectedProtocol + "://"));
            assertDisconnected(output);
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }

    /**
     * Tests implicit CLI settings with empty config file
     * Default config should connect successfully
     */
    @Test
    public void testImplicitSettings() {
        writeJbossCliConfig(null, null, null); //empty config
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

    /**
     * Tests connection to aliased controller with no settings (name only)
     */
    @Test
    public void testImplicitAliasSettings() {
        writeJbossCliConfig(null, null, createControllerAlias(null, null));
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
     * Test to ensure https-remoting defaults to port 9993 when none is specified
     */
    @Test
    public void testHttpsRemotingConnection() {
        Protocol expectedProtocol = Protocol.HTTPS_REMOTING;
        writeJbossCliConfig(
                createDefaultProtocol(true, Protocol.REMOTE), //nonworking default protocol
                createDefaultController(expectedProtocol, null));
        CliProcessWrapper cli = getTestCliProcessWrapper(false);
        try {
            cli.executeNonInteractive();
            String output = cli.getOutput();
            assertTrue(output.contains(":" + 9993));
            assertTrue(output.contains(expectedProtocol + "://"));
            assertDisconnected(output);
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }
}
