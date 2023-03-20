package compiler.memory;

import antlr.SCPPParser;
import compiler.Compiler;

import java.util.Objects;

import static compiler.Compiler.appendLine;
import static compiler.Evaluators.evaluateExpression;
import static compiler.Evaluators.evaluateValue;
import static compiler.Getters.getNamespace;
import static compiler.Getters.getVariable;

public final class Variable {
    public boolean isPublic = false;
    private final VariableType type;
    private int constant;
    private int pointer;
    private String uuid = Compiler.generateUUID();

    public Variable() {
        this.type = Compiler.currentProgram.currentNamespace == null || Compiler.currentProgram.currentFunction == null ? VariableType.GLOBAL : VariableType.LOCAL;

        if (this.type == VariableType.GLOBAL)
            Compiler.dataAppendLine("var" + uuid + " dw 0");
        else {
            this.pointer = -Compiler.currentProgram.currentFunction.varPtr;
            Compiler.currentProgram.currentFunction.varPtr += 4;
        }
    }

    public Variable(int value) {
        this.type = VariableType.IMMEDIATE;
        this.constant = value;
    }

    public Variable(Variable v) {
        this.isPublic = v.isPublic;
        this.type = v.type;
        this.constant = v.constant;
        this.pointer = v.pointer;
        this.uuid = v.uuid;
    }

    private Variable(int customPointer, boolean thisBooleanDoesNothing) {
        this.type = VariableType.LOCAL;
        this.pointer = customPointer;
    }

    public void setTo(String reg) {
        if (type == VariableType.IMMEDIATE)
            Compiler.errorAndKill("INTERNAL ERROR: Attempted to change a constant's value");
        appendLine("mov " + this + ", " + reg);
    }

    public void setTo(SCPPParser.ExpressionContext ctx) {
        if (ctx.value() != null) {
            evaluateValue(ctx.value(), this.toString());
            return;
        }

        evaluateExpression(ctx);
        appendLine("mov " + this + ", eax");
    }

    public void setTo(int value) {
        appendLine("mov " + this + ", " + value);
    }

    public void getTo(String reg) {
        appendLine("mov " + reg + ", " + this);
    }

    public static Variable create() {
        return new Variable();
    }

    public static Variable fromCustomPointer(int ptr) {
        return new Variable(ptr, false);
    }

    public static Variable of(SCPPParser.VariableContext variable) {
        Variable ret;

        if (variable.variable() != null)
            ret = getVariable(getNamespace(variable.ID().getText()), null, variable.variable().getText());
        else
            ret = getVariable(Compiler.currentProgram.currentNamespace,
                    Compiler.currentProgram.currentFunction, variable.ID().getText());
        return ret;
    }

    @Override
    public String toString() {
        return switch (type) {
            case GLOBAL -> "dword var" + uuid;
            case LOCAL -> "dword [ebp" + (pointer < 0 ? ("-" + -pointer) : ("+" + pointer)) + "]";
            case IMMEDIATE -> constant + "";
        };
    }

    public boolean isPublic() {
        return isPublic;
    }

    private enum VariableType {
        GLOBAL, LOCAL, IMMEDIATE
    }
}
