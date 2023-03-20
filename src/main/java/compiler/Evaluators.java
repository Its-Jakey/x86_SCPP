package compiler;

import antlr.SCPPParser;
import compiler.memory.Function;
import compiler.memory.FunctionOrNamespace;
import compiler.memory.Variable;
import compiler.postfixConversion.InfixToPostfix;
import compiler.postfixConversion.ValueOrOperator;

import java.util.ArrayList;
import java.util.List;

import static compiler.Compiler.*;
import static compiler.Getters.*;

public class Evaluators {
    public static List<SCPPParser.ExpressionContext> evaluateArgumentArray(SCPPParser.ArgumentArrayContext ctx) {
        List<SCPPParser.ExpressionContext> ret = new ArrayList<>();
        SCPPParser.ArgumentArrayContext cur = ctx;

        while (cur != null) {
            ret.add(cur.expression());
            cur = cur.argumentArray();
        }
        return ret;
    }

    public static void applyCurrentIndex(String reg, SCPPParser.ArrayIndexContext arrayIndex) {
        appendLine("push " + reg);
        evaluateExpression(arrayIndex.expression(), "ebx");
        appendLine("pop " + reg);
        appendLine("add " + reg + ", ebx");
        appendLine("mov " + reg + ", [" + reg + "]");
    }

    public static void loadValueAtArrayIndex(String arrayReg, SCPPParser.ArrayIndexContext arrayIndex) {
        SCPPParser.ArrayIndexContext cur = arrayIndex;
        while (cur != null) {
            applyCurrentIndex(arrayReg, cur);
            cur = cur.arrayIndex();
        }
    }

    public static void setValueAtArrayIndex(String arrayReg, SCPPParser.ArrayIndexContext arrayIndex, SCPPParser.ExpressionContext value) {
        SCPPParser.ArrayIndexContext cur = arrayIndex;
        while (cur.arrayIndex() != null) {
            applyCurrentIndex(arrayReg, cur);
            cur = cur.arrayIndex();
        }

        appendLine("push " + arrayReg);
        evaluateExpression(cur.expression(), "ebx");
        appendLine("pop " + arrayReg);
        appendLine("add " + arrayReg + ", ebx");

        appendLine("push " + arrayReg);
        evaluateExpression(value, "ebx");
        appendLine("pop " + arrayReg);

        appendLine("mov [" + arrayReg + "], ebx");
    }

    public static void assignArgumentArrayToArray(SCPPParser.ArgumentArrayContext ctx) {
        //TODO Make arrays work
    }

    public static void evaluateValue(SCPPParser.ValueContext ctx, String reg) {
        if (ctx.variable() != null || ctx.functionCall() != null) {

            if (ctx.variable() != null) {
                SCPPParser.VariableContext variable = ctx.variable();
                Variable var;

                if (variable.variable() != null)
                    var = getVariable(getNamespace(variable.ID().getText()), null, variable.variable().ID().getText());
                else
                    var = getVariable(currentProgram.currentNamespace, currentProgram.currentFunction, variable.ID().getText());
                var.getTo(reg);
            } else if (ctx.functionCall() != null)
                evaluateFunctionCall(ctx.functionCall());

            if (ctx.arrayIndex() != null)
                loadValueAtArrayIndex("eax", ctx.arrayIndex());
            return;
        }
        if (ctx.STRING() != null)
            appendLine("mov " + reg + ", " + getAsmString(ctx.STRING().getText()));
        else if (ctx.INT() != null)
            appendLine("mov " + reg + ", " + ctx.INT().getText());
        else if (ctx.HEX() != null)
            appendLine("mov " + reg + ", " + Long.parseLong(ctx.HEX().getText().substring(2), 16));
        else if (ctx.BIN() != null)
            appendLine("mov " + reg + ", " + Long.parseLong(ctx.BIN().getText().substring(2), 2));
        else if (ctx.argumentArray() != null)
            assignArgumentArrayToArray(ctx.argumentArray());
        else {
            SCPPParser.ConditionalValueContext val = ctx.conditionalValue();

            evaluateExpression(val.expression(2), "ebx");
            evaluateExpression(val.expression(1), "ecx");
            evaluateExpression(val.expression(0));

            String set_ecx = "set_ecx" + generateUUID();
            String done = "done" + generateUUID();

            appendLine("cmp eax, 1");
            appendLine("jge " + set_ecx);
            appendLine("mov eax, ebx");
            appendLine("jmp " + done);
            appendLine(set_ecx + ":");
            appendLine("mov eax, ecx");
            appendLine(done + ":");
        }
    }

    public static void evaluateValue(SCPPParser.ValueContext ctx) {
        evaluateValue(ctx, "eax");
    }

    public static void evaluateExpression(SCPPParser.ExpressionContext ctx) {
        evaluateExpression(ctx, "eax");
    }

    public static void evaluateExpression(SCPPParser.ExpressionContext ctx, String register) {
        if (ctx.value() != null) {
            evaluateValue(ctx.value(), register);
            return;
        }

        List<ValueOrOperator> postfix = new ArrayList<>();
        InfixToPostfix.addExpressionToList(ctx, postfix);
        InfixToPostfix.evaluatePostfix(InfixToPostfix.infixToPostfix(postfix), register);
    }

    public static List<Function> functionsCalled = new ArrayList<>();

    public static Function evaluateFunctionCall(SCPPParser.FunctionCallContext ctx) {
        Function ret = getFunction(ctx);
        FunctionOrNamespace tmp = currentProgram.currentFunction == null ?
                new FunctionOrNamespace(null, currentProgram.currentNamespace) :
                new FunctionOrNamespace(currentProgram.currentFunction, null);
        if (!ret.usedBy.contains(tmp))
            ret.usedBy.add(tmp);
        if (!functionsCalled.contains(ret))
            functionsCalled.add(ret);
        ret.call(evaluateArgumentArray(ctx.argumentArray()));
        return ret;
    }

    public static List<String> evaluateFunctionArgumentArray(SCPPParser.FunctionArgumentArrayContext ctx) {
        SCPPParser.FunctionArgumentArrayContext cur = ctx;
        List<String> ret = new ArrayList<>();

        while (cur != null) {
            ret.add(cur.ID().getText());
            cur = cur.functionArgumentArray();
        }
        return ret;
    }
}
