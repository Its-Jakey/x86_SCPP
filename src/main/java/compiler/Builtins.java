package compiler;

import antlr.SCPPParser;
import compiler.memory.Function;
import compiler.memory.Namespace;
import compiler.memory.Variable;
import org.apache.commons.text.StringEscapeUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static compiler.Compiler.*;
import static compiler.Evaluators.evaluateExpression;
import static compiler.Evaluators.evaluateFunctionCall;
import static compiler.Getters.getNamespace;
import static compiler.Getters.getVariable;

public class Builtins extends Namespace {
    public Builtins() {
        super("__builtins__", true);
        addAllArgs(new _asm_());
        add(new exit());
    }

    private void addAllArgs(Function function) {
        for (int i = 0; i < 25; i++)
            super.functions.put(Function.getID(function.name, i), function);
    }
    private void add(Function function) {
        super.functions.put(function.getID(), function);
    }

    private static class _asm_ extends Function {
        public _asm_() {
            super("_asm_", List.of(), true);
        }

        @Override
        public void call(List<SCPPParser.ExpressionContext> args) {
            if (args.size() < 1) {
                error("_asm_ takes at least 1 argument, but 0 were given");
                return;
            }
            if (args.get(0).value() == null || args.get(0).value().STRING() == null)
                errorAndKill("First argument of raw asm must be a string");
            String tmp = args.get(0).value().getText();
            String command = StringEscapeUtils.unescapeJava(tmp.substring(1, tmp.length() - 1));

            int i = 0;
            for (SCPPParser.ExpressionContext expr : args) {
                i++;
                if (i == 1)
                    continue;

                String toAdd = "";

                if (expr.value() == null)
                    errorAndKill("Only literals are allowed in raw assembly");
                SCPPParser.ValueContext value = expr.value();
                if (value.arrayIndex() != null)
                    errorAndKill("Only literals are allowed in raw assembly");
                if (value.variable() != null)
                    toAdd = Variable.of(value.variable()).toString();
                else if (value.functionCall() != null)
                    toAdd = Function.of(value.functionCall()).getLabel();
                else if (value.conditionalValue() != null)
                    errorAndKill("Conditional values not allowed in raw assembly");
                else if (value.STRING() != null)
                    toAdd = getAsmString(value.STRING().getText());
                else
                    toAdd = value.getText();
                command = command.replaceAll("%" + (i - 1), toAdd);
            }

            appendLine(command.replaceAll("\n", "\n" + "    ".repeat(Compiler.tabs)));
        }
    }

    private static class exit extends Function {

        public exit() {
            super("exit", List.of(), true);
        }

        @Override
        public void call(List<SCPPParser.ExpressionContext> args) {
            super.checkArgumentCount(args, false);
            appendLine("mov eax, 1");
            appendLine("xor ebx, ebx");
            appendLine("int 0x80");
        }
    }
}
