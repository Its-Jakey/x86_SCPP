package compiler;

import antlr.SCPPLexer;
import antlr.SCPPListener;
import antlr.SCPPParser;
import compiler.memory.*;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static compiler.Getters.*;
import static compiler.Evaluators.*;

public class Compiler implements SCPPListener {
    public static boolean failed;
    private static int row, col;
    private static List<String> messages;
    private static Stack<Integer> tempStack;
    private static int tempN = 0;
    static final Builtins builtins = new Builtins();
    static LinkedHashMap<String, Integer> constants;
    public static Program currentProgram;
    static LinkedHashMap<String, Program> compiledLibraries;
    static Stack<Switch> switches;
    public static Path topLevelPath;
    static Stack<List<String>> scopes;
    private static final boolean removeUnusedFunctions = false;
    public static StringBuilder text;
    private static StringBuilder data;
    private static StringBuilder externalAsm;
    public static int uuid = (int) (Math.random() * 100000) + 10000;
    private static HashMap<String, String> strings;
    static int tabs = 0;

    public static void dataAppendLine(Object line) {
        data.append("    ").append(line).append("\n");
    }

    public static String getPrefixMessage() {
        return currentProgram.fileName + " " + row + ":" + col;
    }

    public static void error(String msg) {
        messages.add(getPrefixMessage() + " error: " + msg);
        failed = true;
    }

    public static void errorAndKill(String msg) {
        error(msg);
        printMessages();
        Thread.currentThread().stop();
    }

    public static String generateUUID() {
        return Integer.toHexString(uuid++);
    }

    private static final String EXIT_CODE = """
            \s\s\s\smov eax, 1
                xor ebx, ebx
                int 0x80""";

    public static String getAsmString(String s_) {
        String s = s_.substring(1, s_.length() - 1);

        if (strings.containsKey(s))
            return strings.get(s);

        String label = "string" + generateUUID();
        strings.put(s, label);
        dataAppendLine(label + ": db `" + s + "`" + ",0");
        return label;
    }

    static void message(String msg) {
        messages.add("Message: " + msg);
    }

    public static void warn(String msg) {
        messages.add(getPrefixMessage() + " warning: " + msg);
    }

    public static void printMessages() {
        messages.forEach(Console.out::println);
    }

    private Program getLibrary(String lib) {
        if (!compiledLibraries.containsKey(lib)) {
            try {
                final InputStream res = getClass().getResourceAsStream("/lib/" + lib);
                if (res == null)
                    errorAndKill("Unknown library <" + lib + ">");
                else
                    compiledLibraries.put(lib, compileProgramFromString(CharStreams.fromReader(new BufferedReader(new InputStreamReader(res))), 0, lib + ".sc"));
            } catch (IOException e) {
                errorAndKill(e.toString());
            }
        }
        return compiledLibraries.get(lib);
    }

    private static void runWalker(CharStream stream) {
        SCPPLexer lexer = new SCPPLexer(stream);
        SCPPParser parser = new SCPPParser(new CommonTokenStream(lexer));
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                errorAndKill("line " + line + ":" + charPositionInLine + " " + msg);
            }
        });
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(new Compiler(), parser.program());
    }

    public static String compile(Path file) throws IOException {
        long startTime = System.currentTimeMillis();
        topLevelPath = file.getParent();

        init();
        currentProgram = new Program(file.getFileName().toString());
        currentProgram.level = 0;

        runWalker(CharStreams.fromPath(file));
        printMessages();

        end();
        long time = System.currentTimeMillis() - startTime;

        if (failed) {
            printMessages();
            Console.err.println("Build failed in " + time / 1000d + " seconds");
        } else
            Console.out.println("Build succeeded in " + time / 1000d + " seconds");
        return getOutput();
    }

    private static void compileLowerLevel(Path file) {
        Program programBackup = currentProgram.clone();
        currentProgram = compileProgram(file, programBackup.level - 1);

        for (Map.Entry<String, Namespace> newSpace : Objects.requireNonNull(currentProgram).namespaces.entrySet()) {
            if (!programBackup.namespaces.containsKey(newSpace.getKey()) && newSpace.getValue().isPubic)
                programBackup.namespaces.put(newSpace.getKey(), newSpace.getValue());
        }
        currentProgram = programBackup;
    }

    private static Program compileProgram(Path path, int level) {
        if (!path.toFile().exists())
            errorAndKill("File '" + path.getFileName() + "' does not exist");

        try {
            return compileProgramFromString(CharStreams.fromPath(path), level, path.getFileName().toString());
        } catch (IOException e) {
            errorAndKill(e.toString());
        }
        return null;
    }

    private static Program compileProgramFromString(CharStream code, int level, String filename) {
        Program programBackup = currentProgram != null ? currentProgram.clone() : null;

        currentProgram = new Program(filename);
        currentProgram.level = level;

        runWalker(code);
        Program compiledProgram = currentProgram.clone();
        currentProgram = programBackup;
        return compiledProgram;
    }

    public static void compileContext(ParserRuleContext ctx) {
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(new Compiler(), ctx);
    }

    private static void init() {
        tempN = 0;
        failed = false;
        text = new StringBuilder();
        data = new StringBuilder();
        externalAsm = new StringBuilder();
        strings = new HashMap<>();
        messages = new ArrayList<>();
        constants = new LinkedHashMap<>();
        tempStack = new Stack<>();
        compiledLibraries = new LinkedHashMap<>();
        switches = new Stack<>();
        scopes = new Stack<>();
        externalAsmAdded = new ArrayList<>();
    }

    private static void end() {
        Namespace mainNamespace = null;

        for (Namespace namespace : currentProgram.namespaces.values()) {
            if (namespace.level != 0)
                continue;

            if (namespace.functions.containsKey(Function.getID("main", 0)) && namespace.functions.get(Function.getID("main", 0)).isPublic)
                mainNamespace = namespace;
        }
        appendLine("\n_start:");
        if (mainNamespace == null)
            error("main function not found, try defining one with 'public func main(){}'");
        else
            appendLine("    call " + mainNamespace.functions.get(Function.getID("main", 0)).getLabel());
        appendLine(EXIT_CODE);
        appendLine("");

        if (removeUnusedFunctions) {
            text.append(mainNamespace.functions.get(Function.getID("main", 0)).asm);
            functionsCalled.stream().map(f -> f.asm).forEach(text::append);
            //currentProgram.addUsedFunctions();
            //compiledLibraries.values().forEach(Program::addUsedFunctions);
        }
    }

    public static void appendLine(Object line) {
        if (currentProgram != null && currentProgram.currentFunction != null && removeUnusedFunctions)
            currentProgram.currentFunction.myAppendLine("    ".repeat(tabs) + line);
        else
            text.append("\n").append("    ".repeat(tabs)).append(line);
    }

    public static String getOutput() {
        return "section .data\n" + data + "\n" + "section .text\n    global _start" + text.toString() + "\n" + externalAsm;
    }

    public static String createTemp() {
        String ret = "temp" + tempN;
        tempStack.push(tempN);
        tempN++;
        return ret;
    }

    public static String endTemp() {
        return "temp" + tempStack.pop();
    }

    private static void checkInFunction(String keyword) {
        if (currentProgram.currentFunction == null)
            errorAndKill("Cannot use '" + keyword + "' keyword outside of function scope");
    }

    private void combineNamespace(Namespace source, Namespace definition) {
        for (Map.Entry<String, Function> function : source.functions.entrySet()) {
            if (function.getValue().isPublic)
                definition.functions.put(function.getKey(), Function.changeVisibility(function.getValue(), false));
        }
        for (Map.Entry<String, Variable> variable : source.variables.entrySet()) {
            if (variable.getValue().isPublic())
                definition.variables.put(variable.getKey(), new Variable(variable.getValue()));
        }
    }


    @Override
    public void enterProgram(SCPPParser.ProgramContext ctx) {

    }

    @Override
    public void exitProgram(SCPPParser.ProgramContext ctx) {

    }

    @Override
    public void enterStatement(SCPPParser.StatementContext ctx) {

    }

    @Override
    public void exitStatement(SCPPParser.StatementContext ctx) {

    }

    @Override
    public void enterBracketStatement(SCPPParser.BracketStatementContext ctx) {
    }

    @Override
    public void exitBracketStatement(SCPPParser.BracketStatementContext ctx) {
    }

    @Override
    public void enterNamespaceDeclaration(SCPPParser.NamespaceDeclarationContext ctx) {
        if (currentProgram.currentNamespace != null)
            errorAndKill("Cannot declare namespace from inside of namespace");
        if (currentProgram.namespaces.containsKey(ctx.ID(0).getText()))
            errorAndKill("Duplicate namespace '" + ctx.ID(0).getText() + "'");

        if (ctx.codeBlock() == null) {
            if (!currentProgram.namespaces.containsKey(ctx.ID(1).getText()))
                error("Unknown namespace '" + ctx.ID(1).getText() + "'");
            else {
                Namespace namespace = currentProgram.namespaces.get(ctx.ID(1).getText());

                if (namespace.context.codeBlock() == null)
                    error("Namespaces cannot be cloned from a namespace that is not a source (wut am I trying to say here)");
                else {
                    TerminalNode newId = new TerminalNodeImpl(new CommonToken(SCPPParser.ID, ctx.ID(0).getText()));

                    //namespace.ID().set(0, newId); //Not changing ID
                    namespace.context.children.set(0, newId);

                    String fileNameBackup = currentProgram.fileName;
                    currentProgram.fileName = namespace.fileName;
                    compileContext(namespace.context);

                    currentProgram.fileName = fileNameBackup;
                }
            }
            return;
        }
        currentProgram.currentNamespace = new Namespace(ctx.ID(0).getText(), ctx.pub != null);
        currentProgram.currentNamespace.context = ctx;
        currentProgram.currentNamespace.fileName = currentProgram.fileName;
        currentProgram.currentNamespace.level = currentProgram.level;
        SCPPParser.IdListContext uses = ctx.idList();

        while (uses != null) {
            Namespace namespace = getNamespace(uses.ID().getText());
            combineNamespace(namespace, currentProgram.currentNamespace);
            uses = uses.idList();
        }
        //appendLine(":" + currentProgram.currentNamespace.name);
    }

    @Override
    public void exitNamespaceDeclaration(SCPPParser.NamespaceDeclarationContext ctx) {
        if (ctx.codeBlock() != null) {
            currentProgram.namespaces.put(currentProgram.currentNamespace.name, currentProgram.currentNamespace);
            currentProgram.currentNamespace = null;
            //appendLine("ret");
        }
    }

    @Override
    public void enterFunctionDeclaration(SCPPParser.FunctionDeclarationContext ctx) {
        if (currentProgram.currentFunction != null)
            errorAndKill("Cannot define function from inside of function");
        if (currentProgram.currentNamespace == null)
            errorAndKill("Cannot define function from outside of namespace");
        List<String> args = evaluateFunctionArgumentArray(ctx.functionArgumentArray());
        String name = ctx.ID().getText();

        if (currentProgram.currentNamespace.functions.containsKey(Function.getID(name, args.size())) || builtins.functions.containsKey(Function.getID(name, args.size())))
            errorAndKill("Duplicate function '" + name + "'");
        currentProgram.currentFunction = new Function(name, args, ctx.pub != null);
        currentProgram.currentFunction.level = currentProgram.currentNamespace.level;
        currentProgram.currentFunction.inline = ctx.inline != null;
        currentProgram.currentFunction.context = ctx;
        currentProgram.currentFunction.program = currentProgram;

        if (ctx.inline == null) {
            appendLine(currentProgram.currentFunction.getLabel() + ":");
            tabs++;
            appendLine("push ebp");
            appendLine("mov ebp, esp");
        }
    }

    @Override
    public void exitFunctionDeclaration(SCPPParser.FunctionDeclarationContext ctx) {
        currentProgram.currentNamespace.functions.put(currentProgram.currentFunction.getID(), currentProgram.currentFunction);
        if (ctx.inline == null) {
            appendLine("mov esp, ebp");
            appendLine("pop ebp");
            appendLine("ret");
        }
        tabs--;
        appendLine("");
        currentProgram.currentFunction = null;
    }

    private static final Stack<Integer> ifCounts = new Stack<>();
    private static int ifCount = 0;

    @Override
    public void enterIfStatement(SCPPParser.IfStatementContext ctx) {
        ifCounts.push(ifCount++);
    }

    @Override
    public void exitIfStatement(SCPPParser.IfStatementContext ctx) {
        ifCounts.pop();
    }

    @Override
    public void enterIfPart(SCPPParser.IfPartContext ctx) {
        checkInFunction("if");

        evaluateExpression(ctx.expression());
        appendLine("cmp eax, 0");
        appendLine("jle ifExit" + ifCounts.peek());
    }

    @Override
    public void exitIfPart(SCPPParser.IfPartContext ctx) {
        if (((SCPPParser.IfStatementContext) ctx.getParent()).elsePart() != null)
            appendLine("jmp elseExit" + ifCounts.peek());
        appendLine("ifExit" + ifCounts.peek() + ":");
    }

    @Override
    public void enterElsePart(SCPPParser.ElsePartContext ctx) {
        appendLine("elseEnter" + ifCounts.peek() + ":");
    }

    @Override
    public void exitElsePart(SCPPParser.ElsePartContext ctx) {
        appendLine("elseExit" + ifCounts.peek() + ":");
    }

    @Override
    public void enterWhileLoop(SCPPParser.WhileLoopContext ctx) {
        checkInFunction("while");

        evaluateExpression(ctx.expression());
        appendLine("cmp eax, 0");
        appendLine("jle " + createTemp());
        appendLine(createTemp() + ":");
        tabs++;
    }

    @Override
    public void exitWhileLoop(SCPPParser.WhileLoopContext ctx) {
        evaluateExpression(ctx.expression());
        appendLine("cmp eax, 1");
        appendLine("jge " + endTemp());
        tabs--;
        appendLine(endTemp() + ":");
    }

    private static Stack<Object> scopeStuff = new Stack<>();

    @Override
    public void enterForLoop(SCPPParser.ForLoopContext ctx) {
        checkInFunction("for");
        String idxName = ctx.ID().getText();

        if (currentProgram.currentFunction.localVariables.containsKey(idxName) || currentProgram.currentFunction.arguments.containsKey(idxName))
            errorAndKill("Duplicate variable '" + idxName + "'");
        Variable var = Variable.create();
        var.setTo(ctx.expression(0));

        if (ctx.expression().size() > 2) {
            Variable by = Variable.create();
            by.setTo(ctx.expression(2));
            scopeStuff.push(by);
        }
        Variable highValue = Variable.create();
        highValue.setTo(ctx.expression(1));

        appendLine(createTemp() + ":");
        tabs++;

        var.getTo("eax");
        highValue.getTo("ebx");
        appendLine("cmp eax, ebx");
        appendLine("jge " + createTemp());

        currentProgram.currentFunction.localVariables.put(ctx.ID().getText(), var);

        /*
        tempStack
        -----------
        forEnd
        boundsCheck
        increment?
        -----------
         */
    }

    @Override
    public void exitForLoop(SCPPParser.ForLoopContext ctx) {
        String forEnd = endTemp(), boundsCheck = endTemp();
        Variable var = currentProgram.currentFunction.localVariables.get(ctx.ID().getText());
        var.getTo("eax");

        if (ctx.expression().size() > 2) { //TODO: Add "changeVarBy" instruction
            Variable by = (Variable) scopeStuff.pop();
            by.getTo("ebx");
            appendLine("add eax, ebx");
            var.setTo("eax");
        } else {
            appendLine("inc eax");
            var.setTo("eax");
        }
        appendLine("jmp " + boundsCheck);
        tabs--;
        appendLine(forEnd + ":");

        currentProgram.currentFunction.localVariables.remove(ctx.ID().getText());
    }

    @Override
    public void enterSwitchStatement(SCPPParser.SwitchStatementContext ctx) {
        Variable variable = Variable.create();
        variable.setTo(ctx.expression());

        String exit = createTemp();
        endTemp();

        switches.push(new Switch(variable, exit));
    }

    @Override
    public void exitSwitchStatement(SCPPParser.SwitchStatementContext ctx) {
        appendLine(switches.pop().exitPoint + ":");
    }

    @Override
    public void enterCaseStatement(SCPPParser.CaseStatementContext ctx) {
        appendLine("case" + ctx.hashCode() + ":");

        evaluateExpression(ctx.expression());
        switches.peek().switchValue.setTo("ebx");
        appendLine("cmp eax, ebx");
        SCPPParser.SwitchStatementContext ssc = ((SCPPParser.SwitchStatementContext) ctx.parent);

        if (ssc.caseStatement().indexOf(ctx) == ssc.caseStatement().size() - 1) { //If this is the last case statement in the chain
            if (ssc.defaultStatement() != null)
                appendLine("jne default" + switches.peek().hashCode()); //Jump to the default statement
            else
                appendLine("jne " + switches.peek().exitPoint);
        } else {
            //Not a real label for some reason
            appendLine("jne case" + ssc.caseStatement().get(ssc.caseStatement().indexOf(ctx) + 1).hashCode()); //Jump to the next case statement
        }
    }

    @Override
    public void exitCaseStatement(SCPPParser.CaseStatementContext ctx) {
        appendLine("jmp " + switches.peek().exitPoint);
    }

    @Override
    public void enterDefaultStatement(SCPPParser.DefaultStatementContext ctx) {
        appendLine("default" + switches.peek().hashCode() + ":");
    }

    @Override
    public void exitDefaultStatement(SCPPParser.DefaultStatementContext ctx) {

    }

    @Override
    public void enterNonBracketStatement(SCPPParser.NonBracketStatementContext ctx) {

    }

    @Override
    public void exitNonBracketStatement(SCPPParser.NonBracketStatementContext ctx) {

    }

    @Override
    public void enterVariableDeclaration(SCPPParser.VariableDeclarationContext ctx) {

    }

    @Override
    public void exitVariableDeclaration(SCPPParser.VariableDeclarationContext ctx) {
        if (currentProgram.currentNamespace == null) {
            error("Cannot define variable outside of namespace bounds");
            return;
        }
        Variable var = Variable.create();
        if (currentProgram.currentFunction != null) {
            if (currentProgram.currentFunction.localVariables.containsKey(ctx.ID().getText()) || currentProgram.currentFunction.arguments.containsKey(ctx.ID().getText()))
                error("Duplicate variable '" + ctx.ID().getText() + "'");

            scopes.peek().add(ctx.ID().getText());
        } else {
            if (currentProgram.currentNamespace.variables.containsKey(ctx.ID().getText()))
                error("Duplicate variable '" + ctx.ID().getText() + "'");

            var.isPublic = ctx.pub != null;
        }
        currentProgram.currentFunction.localVariables.put(ctx.ID().getText(), var);
        var.setTo(ctx.expression());
    }

    @Override
    public void enterVariableValueChange(SCPPParser.VariableValueChangeContext ctx) {

    }

    @Override
    public void exitVariableValueChange(SCPPParser.VariableValueChangeContext ctx) {
        if (currentProgram.currentFunction == null)
            errorAndKill("Cannot change variable value outside of function bounds.");
        Variable var = Variable.of(ctx.variable());

        if (ctx.VARIABLE_MODIFIER() != null) {
            if (ctx.arrayIndex() != null)
                error("Cannot use array index with +=, -=, *=, and /=");
            else {
                //TODO
            }
        } else if (ctx.VARIABLE_SINGLE_MODIFIER() != null) {
            var.getTo("eax");
            if (ctx.VARIABLE_SINGLE_MODIFIER().getText().equals("++"))
                appendLine("inc eax");
            else
                appendLine("dec eax");
            var.setTo("eax");
        } else {
            if (ctx.arrayIndex() != null) {
                var.getTo("eax");
                setValueAtArrayIndex("eax", ctx.arrayIndex(), ctx.expression());
                return;
            }
            var.setTo(ctx.expression());
        }
    }

    @Override
    public void enterFunctionCall(SCPPParser.FunctionCallContext ctx) {

    }

    @Override
    public void exitFunctionCall(SCPPParser.FunctionCallContext ctx) {
        if (!(ctx.parent instanceof SCPPParser.ValueContext))
            evaluateFunctionCall(ctx);
    }

    @Override
    public void enterReturnStatement(SCPPParser.ReturnStatementContext ctx) {

    }

    @Override
    public void exitReturnStatement(SCPPParser.ReturnStatementContext ctx) {
        checkInFunction("return");
        if (ctx.expression() != null)
            evaluateExpression(ctx.expression());
        //TODO: Make inline functions non-returnable
        appendLine("ret");
    }

    @Override
    public void enterDirective(SCPPParser.DirectiveContext ctx) {

    }

    @Override
    public void exitDirective(SCPPParser.DirectiveContext ctx) {

    }

    @Override
    public void enterDefineDirective(SCPPParser.DefineDirectiveContext ctx) {

    }

    @Override
    public void exitDefineDirective(SCPPParser.DefineDirectiveContext ctx) {
        addConstant(ctx.ID().getText(), Integer.parseInt(ctx.INT() != null ? ctx.INT().getText() : (ctx.HEX() != null ? String.valueOf(Integer.parseInt(ctx.HEX().getText().substring(2), 16)) : String.valueOf(Integer.parseInt(ctx.BIN().getText().substring(2), 2)))));
        //addConstant(ctx.ID().getText(), ctx.INT().getText());
    }

    @Override
    public void enterIncludeDirective(SCPPParser.IncludeDirectiveContext ctx) {

    }

    private static List<String> externalAsmAdded;

    @Override
    public void exitIncludeDirective(SCPPParser.IncludeDirectiveContext ctx) {
        Program program;

        if (ctx.LIBRARY() != null) {
            String lib = ctx.LIBRARY().getText().substring(1, ctx.LIBRARY().getText().length() - 1);
            if (lib.endsWith(".asm")) {
                String path = "/lib/" + lib;
                if (externalAsmAdded.contains(path))
                    return;

                externalAsmAdded.add(path);
                try {
                    externalAsm.append(readAll(getClass().getResourceAsStream(path))).append("\n");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return;
            } else
                program = getLibrary(lib);
            //Console.out.println(program.fileName);
        } else {
            String tmp = ctx.STRING().getText();
            String path = topLevelPath + "/" + tmp.substring(1, tmp.length() - 1);

            Path filePath = Path.of(path);
            if (path.endsWith(".asm")) {
                if (externalAsmAdded.contains(path))
                    return;
                externalAsmAdded.add(path);
                try {
                    externalAsm.append(Files.readString(filePath)).append("\n");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return;
            }

            if (!compiledLibraries.containsKey(path))
                compiledLibraries.put(path, compileProgram(filePath, currentProgram.level + 1));
            program = compiledLibraries.get(path);

            if (program.namespaces.values().stream().anyMatch(Objects::isNull))
                errorAndKill("Found null namespace while compiling");
        }
        for (Map.Entry<String, Namespace> namespace : program.namespaces.entrySet()) {
            if (!namespace.getValue().isPubic || currentProgram.namespaces.containsKey(namespace.getKey()))
                continue;
            currentProgram.namespaces.put(namespace.getKey(), namespace.getValue());
        }
    }

    @Override
    public void enterCodeBlock(SCPPParser.CodeBlockContext ctx) {
        if (currentProgram.currentFunction != null)
            scopes.push(new ArrayList<>());
    }

    @Override
    public void exitCodeBlock(SCPPParser.CodeBlockContext ctx) {
        if (currentProgram.currentFunction != null) {
            List<String> variablesToRemove = scopes.pop();

            for (String variable : variablesToRemove)
                currentProgram.currentFunction.localVariables.remove(variable);
        }
    }

    @Override
    public void enterArgumentArray(SCPPParser.ArgumentArrayContext ctx) {

    }

    @Override
    public void exitArgumentArray(SCPPParser.ArgumentArrayContext ctx) {

    }

    @Override
    public void enterFunctionArgumentArray(SCPPParser.FunctionArgumentArrayContext ctx) {

    }

    @Override
    public void exitFunctionArgumentArray(SCPPParser.FunctionArgumentArrayContext ctx) {

    }

    @Override
    public void enterArrayIndex(SCPPParser.ArrayIndexContext ctx) {

    }

    @Override
    public void exitArrayIndex(SCPPParser.ArrayIndexContext ctx) {

    }

    @Override
    public void enterVariable(SCPPParser.VariableContext ctx) {

    }

    @Override
    public void exitVariable(SCPPParser.VariableContext ctx) {

    }

    @Override
    public void enterExpression(SCPPParser.ExpressionContext ctx) {

    }

    @Override
    public void exitExpression(SCPPParser.ExpressionContext ctx) {

    }

    @Override
    public void enterValue(SCPPParser.ValueContext ctx) {

    }

    @Override
    public void exitValue(SCPPParser.ValueContext ctx) {

    }

    @Override
    public void enterConditionalValue(SCPPParser.ConditionalValueContext ctx) {

    }

    @Override
    public void exitConditionalValue(SCPPParser.ConditionalValueContext ctx) {

    }

    @Override
    public void enterIdList(SCPPParser.IdListContext ctx) {

    }

    @Override
    public void exitIdList(SCPPParser.IdListContext ctx) {

    }

    @Override
    public void visitTerminal(TerminalNode terminalNode) {

    }

    @Override
    public void visitErrorNode(ErrorNode errorNode) {

    }

    @Override
    public void enterEveryRule(ParserRuleContext parserRuleContext) {
        row = parserRuleContext.start.getLine();
        col = parserRuleContext.start.getCharPositionInLine();
    }

    @Override
    public void exitEveryRule(ParserRuleContext parserRuleContext) {

    }

    public static String readAll(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line).append("\n");
        }
        return stringBuilder.substring(1);
    }
}
