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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

import java.io.ByteArrayOutputStream;

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

    @Before
    @After
    public void removeTestResource() throws Exception {
        final ByteArrayOutputStream cliOut = new ByteArrayOutputStream();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        ctx.connectController();
        try {
            ctx.handle("/system-property=" + TEST_RESOURCE_NAME + ":remove");
        } catch (CommandLineException ex) {
            assertTrue(ex.getMessage().contains("not found"));
        } finally {
            ctx.terminateSession();
        }
    }

    /**
     * Tests whitespace in the middle of words
     * Regression test for https://issues.jboss.org/browse/JBEAP-4536
     *
     * @throws Exception
     */
    @Test
    public void testWhitespaceInMiddle() throws Exception {
        testWrapper("Hello World!", "Hello World!", Delimiter.DOUBLE_QUOTE);
        testWrapper("Hello World!", "Hello World!", Delimiter.CURLY_BRACE);
        testWrapper("Hello\\ World!", "Hello World!", Delimiter.NONE);
    }

    /**
     * Tests whitespace at the start/end of strings and if it is trimmed
     * Double quotes preserve whitespace, curly braces and no delimiter trims
     *
     * @throws Exception
     */
    @Test
    public void testWhitespaceTrimming() throws Exception {
        testWrapper("   Hello World!   ", "   Hello World!   ", Delimiter.DOUBLE_QUOTE);
        testWrapper("   Hello World!   ", "Hello World!", Delimiter.CURLY_BRACE);
        testWrapper("   Hello\\ World!   ", "Hello World!", Delimiter.NONE);
    }

    /**
     * Tests for single quote in a property name
     *
     * @throws Exception
     */
    @Test
    public void testSingleQuotes() throws Exception {
        testWrapper("It's", "It's", Delimiter.DOUBLE_QUOTE);
        testWrapper("It\\'s", "It's", Delimiter.NONE);
        testWrapper("''It's''", "''It's''", Delimiter.DOUBLE_QUOTE);
    }

    /**
     * Tests the usage of commas inside double quotes
     *
     * @throws Exception
     */
    @Test
    public void testCommasInDoubleQuotes() throws Exception {
        testWrapper("Last,First", "Last,First", Delimiter.DOUBLE_QUOTE);
        testWrapper(",,,A,B,C,D,E,F,,,", ",,,A,B,C,D,E,F,,,", Delimiter.DOUBLE_QUOTE);
    }

    /**
     * Tests usage of parenthesis with all delimiter options
     *
     * @throws Exception
     */
    @Test
    public void testParenthesis() throws Exception {
        testWrapper("one(1)", "one(1)", Delimiter.DOUBLE_QUOTE);
        testWrapper("one(1)", "one(1)", Delimiter.CURLY_BRACE);
        testWrapper("one\\(1\\)", "one(1)", Delimiter.NONE);
    }

    /**
     * Tests usage of braces inside double quotes
     *
     * @throws Exception
     */
    @Test
    public void testBraces() throws Exception {
        testWrapper("{braces}", "{braces}", Delimiter.DOUBLE_QUOTE);
        testWrapper("{{}}braces{{{{}}}}", "{{}}braces{{{{}}}}", Delimiter.DOUBLE_QUOTE);
    }

    /**
     * Tests diacritic marks without delimiters and inside curly braces.
     * Regression test against https://issues.jboss.org/browse/JBEAP-5568
     * @throws Exception
     */
    @Test
    @Ignore("JBEAP-5568")
    public void testDiacriticMarks() throws Exception {
        testWrapper("Año", "Año", Delimiter.NONE);
        testWrapper("Dos años", "Dos años", Delimiter.CURLY_BRACE);
        testWrapper("Dos\\ años", "Dos años", Delimiter.NONE);
    }

    /**
     * Tests setting and reading resource value in both interactive and non-interactive
     *
     * @param input property value to set via CLI
     * @param expected property value expected to be set
     * @param delimiter type of delimiter to use for property name escaping
     * @throws Exception
     */
    private void testWrapper(String input, String expected, Delimiter delimiter) throws Exception {
        testInteractive(input, expected, delimiter);
        testNonInteractive(input, expected, delimiter);
    }

    /**
     * Tests setting resource value and verifies it was saved successfully in interactive (user) mode
     *
     * @param input property value to set via CLI
     * @param expected property value expected to be set
     * @param delimiter type of delimiter to use for property name escaping
     * @throws Exception
     */
    private void testInteractive(String input, String expected, Delimiter delimiter) throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper().addCliArgument("--connect");
        try {
            cli.executeInteractive();
            cli.clearOutput();
            cli.pushLineAndWaitForResults("/system-property=" + TEST_RESOURCE_NAME + ":remove");
            cli.clearOutput();

            cli.pushLineAndWaitForResults("/system-property=" + TEST_RESOURCE_NAME +
                    ":add(value=" + delimiter.getStartDelimiter() + input + delimiter.getEndDelimiter() + ")");
            String writeResult = cli.getOutput();
            assertTrue(writeResult.contains("\"outcome\" => \"success\""));
            cli.clearOutput();

            cli.pushLineAndWaitForResults("/system-property=" + TEST_RESOURCE_NAME + ":read-attribute(name=value)");
            String readResult = cli.getOutput();
            cli.clearOutput();

            assertTrue(readResult.contains("\"outcome\" => \"success\""));
            assertTrue(readResult.contains(expected));
            cli.clearOutput();

            cli.pushLineAndWaitForResults("/system-property=" + TEST_RESOURCE_NAME + ":remove");
            assertTrue(cli.getOutput().contains("\"outcome\" => \"success\""));
            cli.pushLineAndWaitForResults("quit");
        } finally {
            cli.destroyProcess();
        }
    }

    /**
     * Tests setting resource value and verifies it was saved successfully in non-interactive mode
     *
     * @param input property value to set via CLI
     * @param expected property value expected to be set
     * @param delimiter type of delimiter to use for property name escaping
     * @throws Exception
     */
    private void testNonInteractive(String input, String expected, Delimiter delimiter) throws Exception {
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
        //add resource
        ctx.handle("/system-property=" + TEST_RESOURCE_NAME +
                ":add(value=" + delimiter.getStartDelimiter() + input + delimiter.getEndDelimiter() + ")");
        String setOutcome = cliOut.toString();
        assertTrue(setOutcome.contains("\"outcome\" => \"success\""));
        cliOut.reset();
        //read resource
        ctx.handle("/system-property=" + TEST_RESOURCE_NAME + ":read-attribute(name=value)");
        String readResult = cliOut.toString();
        assertTrue(readResult.contains(expected));
        assertTrue(readResult.contains("\"outcome\" => \"success\""));
        cliOut.reset();
        //delete resource
        ctx.handle("/system-property=" + TEST_RESOURCE_NAME + ":remove");
        String removeResult = cliOut.toString();
        assertTrue(removeResult.contains("\"outcome\" => \"success\""));
    }

    private enum Delimiter {
        NONE("",""),
        DOUBLE_QUOTE ("\"", "\""),
        CURLY_BRACE("{","}");

        private final String startDelimiter;
        private final String endDelimiter;

        Delimiter(String startDelimiter, String endDelimiter) {
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
