package org.jboss.as.test.integration.management.cli;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by mschvarc on 09/08/16.
 */
@RunWith(WildflyTestRunner.class)
public class CliUTF8TestCase {

    private final String[] testValues = new String[]{
            "qa_value1_23", //passing both tests
            //all below do NOT pass in interactive mode under AESH
            "Año", // https://issues.jboss.org/browse/JBEAP-5568 ;; "result" => "Aoo"
            "aábcčdďeéěfghchiíjklmnňoópqrřsštťuúůvwxyýzž", //czech alphabet lowercase
            "AÁBCČDĎEÉĚFGHChIÍJKLMNŇOÓPQRŘSŠTŤUÚŮVWXYÝZŽ", //czech alphabet uppercase
            /*
                This string has the pattern {[utf8][ASCII128]}, results in {[ASCII128][ASCII128]} and succeeds with WRONG value
                "outcome" => "success",
                "result" => "SSoommee ssaammppllee AASSCCIIII-tteexxtt ffoorr tteessttiinngg" //notice the duplication
            */
            "ŞSồoოmềe şsẫaოmρpĺlée ΆAŜSÇCĬIĨI-ţtẽeхxţt ƒfōoяr ţtệeşsťtïiņnğg", //https://www.tienhuis.nl/utf8-generator
            "Şồოề şẫოρĺé ΆŜÇĬĨ-ţẽхţ ƒōя ţệşťïņğ", //https://www.tienhuis.nl/utf8-generator
            "\u2318\u2613" + "a" + "\u2312\u2610",
            "\u2318\u2613\u2312\u2610",
            "☀E★m☆o☇j☑i☒☓\u2613", //https://en.wikipedia.org/wiki/Miscellaneous_Symbols
            "Wȟíťě šṕáčé", //https://issues.jboss.org/browse/JBEAP-4529
            "АаБбВвГгДдЕеЁёЖжЗзИиЙйКкЛлМмНнОоПпРрСсТтУуФфХхЦцЧчШшЩщЪъЫыЬьЭэЮюЯя", //cyrilic
    };

    @Test
    public void testValuesNonInteractive() throws Exception {
        int successCounter = 0;

        final ByteArrayOutputStream cliOut = new ByteArrayOutputStream();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        ctx.connectController();

        for (String expected : testValues) {
            cliOut.reset();
            try {
                ctx.handle("/system-property=test1:remove");
            } catch (CommandLineException e) {
                assertTrue(e.getMessage().contains("not found"));
            }
            cliOut.reset();
            ctx.handle("/system-property=test1:add(value={" + expected + "})");
            String setOutcome = cliOut.toString();
            cliOut.reset();
            ctx.handle("/system-property=test1:read-attribute(name=value)");
            String echoResult = cliOut.toString();

            successCounter += (echoResult.contains(expected) && echoResult.contains("success")) ? 1 : 0;
        }
        assertEquals(testValues.length, successCounter);
    }

    @Test
    public void testValuesInteractive() throws Exception {
        int successCounter = 0;
        CliProcessWrapper cli = new CliProcessWrapper().addCliArgument("--connect");
        try {
            cli.executeInteractive();
            cli.clearOutput();
            for (String expected : testValues) {
                cli.pushLineAndWaitForResults("/system-property=test1:remove");
                String out1 = cli.getOutput();
                cli.clearOutput();

                cli.pushLineAndWaitForResults("/system-property=test1:add(value={" + expected + "})");
                String out2 = cli.getOutput();
                cli.clearOutput();

                cli.pushLineAndWaitForResults("/system-property=test1:read-attribute(name=value)");
                String readResult = cli.getOutput();
                cli.clearOutput();

                boolean success = (readResult.contains(expected) && readResult.contains("success"));
                successCounter += success ? 1 : 0;
                if (!success && readResult.contains("success")) {
                    System.err.println("\n" + expected + " \n" + readResult);
                }
            }
            cli.ctrlCAndWaitForClose();
        } finally {
            cli.destroyProcess();
        }
        assertEquals(testValues.length, successCounter);
    }
}
