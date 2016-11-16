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

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertTrue;

/**
 * Tests for setting values containing special characters via CLI
 * Regression testing for https://issues.jboss.org/browse/JBEAP-4536
 *
 * @author Martin Schvarcbacher
 */
@RunWith(WildflyTestRunner.class)
public class CliSpecialCharactersTestCase {

    private static final String TEST_RESOURCE_NAME = "test_resource_special_chars";

    private void removeTestResources() throws Exception {
        final ByteArrayOutputStream cliOut = new ByteArrayOutputStream();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.connectController();
            ctx.handleSafe("/system-property=" + TEST_RESOURCE_NAME + ":remove");
            ctx.disconnectController();
        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void loopTheLoop() throws Exception {
        for (int i = 0; i < 10; i++) {
            testBraces();
            testCommasInDoubleQuotes();
            testParenthesis();
            testSingleQuotes();
            testWhitespaceInMiddle();
            testWhitespaceTrimming();
        }
    }


    @Before
    public void setup() throws Exception {
        removeTestResources();
    }

    @After
    public void cleanup() throws Exception {
        removeTestResources();
    }

    /**
     * Tests whitespace in the middle of words
     * Regression test for https://issues.jboss.org/browse/JBEAP-4536
     *
     * @throws Exception
     */
    @Test
    public void testWhitespaceInMiddle() throws Exception {
        testInteractiveAndNonInteractive("Hello World!", "Hello World!", Delimiters.DOUBLE_QUOTE);
        testInteractiveAndNonInteractive("Hello World!", "Hello World!", Delimiters.CURLY_BRACE);
        testInteractiveAndNonInteractive("Hello\\ World!", "Hello World!", Delimiters.NONE);
    }

    /**
     * Tests whitespace at the start/end of strings and if it is trimmed
     * Double quotes preserve whitespace, curly braces and no delimiter trims
     *
     * @throws Exception
     */
    @Test
    public void testWhitespaceTrimming() throws Exception {
        testInteractiveAndNonInteractive("   Hello World!   ", "   Hello World!   ", Delimiters.DOUBLE_QUOTE);
        testInteractiveAndNonInteractive("   Hello World!   ", "Hello World!", Delimiters.CURLY_BRACE);
        testInteractiveAndNonInteractive("   Hello\\ World!   ", "Hello World!", Delimiters.NONE);
    }

    /**
     * Tests for single quote in a property name
     *
     * @throws Exception
     */
    @Test
    public void testSingleQuotes() throws Exception {
        testInteractiveAndNonInteractive("It's", "It's", Delimiters.DOUBLE_QUOTE);
        testInteractiveAndNonInteractive("It\\'s", "It's", Delimiters.NONE);
        testInteractiveAndNonInteractive("''It's''", "''It's''", Delimiters.DOUBLE_QUOTE);
    }

    /**
     * Tests the usage of commas inside double quotes
     *
     * @throws Exception
     */
    @Test
    public void testCommasInDoubleQuotes() throws Exception {
        testInteractiveAndNonInteractive("Last,First", "Last,First", Delimiters.DOUBLE_QUOTE);
        testInteractiveAndNonInteractive(",,,A,B,C,D,E,F,,,", ",,,A,B,C,D,E,F,,,", Delimiters.DOUBLE_QUOTE);
    }

    /**
     * Tests usage of parenthesis with all delimiter options
     *
     * @throws Exception
     */
    @Test
    public void testParenthesis() throws Exception {
        testInteractiveAndNonInteractive("one(1)", "one(1)", Delimiters.DOUBLE_QUOTE);
        testInteractiveAndNonInteractive("one(1)", "one(1)", Delimiters.CURLY_BRACE);
        testInteractiveAndNonInteractive("one\\(1\\)", "one(1)", Delimiters.NONE);
    }

    /**
     * Tests usage of braces inside double quotes
     *
     * @throws Exception
     */
    @Test
    public void testBraces() throws Exception {
        testInteractiveAndNonInteractive("{braces}", "{braces}", Delimiters.DOUBLE_QUOTE);
    }

    /**
     * Tests setting and reading resource value in both interactive and non-interactive
     *
     * @param input     property value to set via CLI
     * @param expected  property value expected to be set
     * @param delimiter type of delimiter to use for property name escaping
     * @throws Exception
     */
    private void testInteractiveAndNonInteractive(String input, String expected, Delimiters delimiter) throws Exception {
        testInteractive(input, expected, delimiter);
        testNonInteractive(input, expected, delimiter);
    }

    /**
     * Tests setting resource value and verifies it was saved successfully in interactive (user) mode
     *
     * @param input     property value to set via CLI
     * @param expected  property value expected to be set
     * @param delimiter type of delimiter to use for property name escaping
     * @throws IOException
     */
    private synchronized void testInteractive(String input, String expected, Delimiters delimiter) throws Exception {
        removeTestResources();
        final CliProcessWrapper cli = new CliProcessWrapper().addCliArgument("-c");
        cli.executeInteractive();
        try {
            cli.clearOutput();
            cli.pushLineAndWaitForResults("/system-property=" + TEST_RESOURCE_NAME +
                    ":add(value=" + delimiter.getStartDelimiter() + input + delimiter.getEndDelimiter() + ")");
            String writeResult = cli.getOutput();
            assertTrue(writeResult.contains("\"outcome\" => \"success\""));
            System.err.println(cli.getOutput());
            cli.clearOutput();

            cli.pushLineAndWaitForResults("/system-property=" + TEST_RESOURCE_NAME + ":read-attribute(name=value)");
            System.err.println(cli.getOutput());
            String readResult = cli.getOutput();

            assertTrue(readResult.contains("\"outcome\" => \"success\""));
            System.err.println(cli.getOutput());
            assertTrue(readResult.contains(expected));
            cli.clearOutput();

            cli.pushLineAndWaitForResults("/system-property=" + TEST_RESOURCE_NAME + ":remove");
            System.err.println(cli.getOutput());
            assertTrue(cli.getOutput().contains("\"outcome\" => \"success\""));
            //cli.pushLineAndWaitForResults("quit");
            cli.pushLineAndWaitForClose("quit");
            //boolean closed = cli.ctrlCAndWaitForClose();
            //assertTrue("Process did not terminate correctly. Output: '" + cli.getOutput() + "'", closed);
        } finally {
            cli.destroyProcess();
        }
    }

    /**
     * Tests setting resource value and verifies it was saved successfully in non-interactive mode
     *
     * @param input     property value to set via CLI
     * @param expected  property value expected to be set
     * @param delimiter type of delimiter to use for property name escaping
     * @throws CommandLineException
     */
    private void testNonInteractive(String input, String expected, Delimiters delimiter) throws CommandLineException {
        final ByteArrayOutputStream cliOut = new ByteArrayOutputStream();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        ctx.connectController();
        cliOut.reset();
        try {
            ctx.handle("/system-property=" + TEST_RESOURCE_NAME + ":remove");
        } catch (CommandLineException e) {
            assertTrue(e.getMessage().contains("not found"));
        }
        cliOut.reset();
        ctx.handle("/system-property=" + TEST_RESOURCE_NAME +
                ":add(value=" + delimiter.getStartDelimiter() + input + delimiter.getEndDelimiter() + ")");
        String setOutcome = cliOut.toString();
        assertTrue(setOutcome.contains("\"outcome\" => \"success\""));
        cliOut.reset();
        ctx.handle("/system-property=" + TEST_RESOURCE_NAME + ":read-attribute(name=value)");
        String readResult = cliOut.toString();
        assertTrue(readResult.contains(expected));
        assertTrue(readResult.contains("\"outcome\" => \"success\""));
        cliOut.reset();
        ctx.handle("/system-property=" + TEST_RESOURCE_NAME + ":remove");
        String removeResult = cliOut.toString();
        assertTrue(removeResult.contains("\"outcome\" => \"success\""));
    }

    private enum Delimiters {
        NONE("", ""),
        DOUBLE_QUOTE("\"", "\""),
        CURLY_BRACE("{", "}");

        private final String startDelimiter;
        private final String endDelimiter;

        Delimiters(String startDelimiter, String endDelimiter) {
            this.startDelimiter = startDelimiter;
            this.endDelimiter = endDelimiter;
        }

        public String getStartDelimiter() {
            return startDelimiter;
        }

        public String getEndDelimiter() {
            return endDelimiter;
        }
    }
}
