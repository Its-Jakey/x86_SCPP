package compiler.memory;

import antlr.SCPPParser;
import compiler.Compiler;
import compiler.Evaluators;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static compiler.Compiler.appendLine;
import static compiler.Compiler.currentProgram;
import static compiler.Evaluators.evaluateExpression;
import static compiler.Getters.getFunction;

public class Function {
    public final LinkedHashMap<String, Variable> arguments;
    public final LinkedHashMap<String, Variable> localVariables;
    public final boolean isPublic;

    public final String name;
    public SCPPParser.FunctionDeclarationContext context = null;
    public Program program = null;
    public boolean inline = false;
    public int level = 0;
    public final List<String> rawArgs;
    public final StringBuilder asm;
    public final List<FunctionOrNamespace> usedBy = new ArrayList<>();
    private final String uuid;
    public int varPtr = 4;

    public Function(String name, List<String> arguments, boolean isPublic) {
        this.arguments = new LinkedHashMap<>();
        this.localVariables = new LinkedHashMap<>();
        this.isPublic = isPublic;
        this.name = name;
        this.rawArgs = arguments;
        this.asm = new StringBuilder();
        this.uuid = Compiler.generateUUID();

        int argNum = 8;
        for (String arg : arguments) {
            this.arguments.put(arg, Variable.fromCustomPointer(argNum));
            argNum += 4;
        }
    }

    private Function(Function source, boolean isPublic) {
        this.arguments = source.arguments;
        this.localVariables = source.localVariables;
        this.isPublic = isPublic;
        this.name = source.name;
        this.context = source.context;
        this.program = source.program;
        this.inline = source.inline;
        this.level = source.level;
        this.rawArgs = source.rawArgs;
        this.asm = new StringBuilder();
        this.uuid = source.uuid;
    }

    public void myAppendLine(Object line) {
        asm.append("\n").append(line);
    }

    public boolean isUsed(int depth) {
        //return true;
        if (name.equals("main") && arguments.size() == 0)
            return true;
        //return usedBy.size() > 0;

        if (depth >= 2)
            return usedBy.size() > 0;
        for (FunctionOrNamespace user : usedBy) {
            if (user.namespace() != null)
                return true;
            return user.function().isUsed(depth + 1);
        }
        return false;

    }

    public String getLabel() {
        return getID() + uuid;
    }

    public void call(List<SCPPParser.ExpressionContext> args) {
        checkArgumentCount(args, false);

        appendLine("sub esp, " + currentProgram.currentFunction.varPtr);
        for (int i = args.size() - 1; i >= 0; i--) {
            evaluateExpression(args.get(i));
            appendLine("push eax");
        }
        if (inline) {
            Program programBackup = Compiler.currentProgram.clone();
            Compiler.currentProgram.currentFunction = this;

            Compiler.compileContext(context.codeBlock());
            Compiler.currentProgram = programBackup;
        } else
            Compiler.appendLine("call " + getLabel());
        if (args.size() > 0)
            appendLine("add esp, " + (args.size() * 4));
        appendLine("add esp, " + currentProgram.currentFunction.varPtr);
    }

    public void checkArgumentCount(List<SCPPParser.ExpressionContext> args, boolean failsafe) {
        if (args.size() != arguments.size()) {
            Compiler.error(name + " takes " + arguments.size() + " arguments, but " + args.size() + " were given.");

            if (failsafe) {
                Compiler.printMessages();
                System.exit( 1);
            }
        }
    }

    public static Function changeVisibility(Function function, boolean isPublic) {
        return new Function(function, isPublic);
    }

    public static String getID(String name, List<SCPPParser.ExpressionContext> args) {
        return name + "_" + args.size();
    }
    public static String getID(String name, int args) {
        return name + "_" + args;
    }
    public String getID() {
        return name + "_" + arguments.size();
    }
    private boolean added = false;

    public void add() {
        if (!added) {
            Compiler.text.append(asm);
            added = true;
        }
    }

    public static Function of(SCPPParser.FunctionCallContext ctx) {
        Function ret = getFunction(ctx);
        FunctionOrNamespace tmp = currentProgram.currentFunction == null ?
                new FunctionOrNamespace(null, currentProgram.currentNamespace) :
                new FunctionOrNamespace(currentProgram.currentFunction, null);
        if (!ret.usedBy.contains(tmp))
            ret.usedBy.add(tmp);
        if (!Evaluators.functionsCalled.contains(ret))
            Evaluators.functionsCalled.add(ret);
        return ret;
    }
}
