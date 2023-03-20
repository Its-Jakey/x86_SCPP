package compiler.memory;

import antlr.SCPPParser;
import compiler.Compiler;

import java.util.LinkedHashMap;

public class Namespace {
    public final LinkedHashMap<String, Variable> variables;
    public final LinkedHashMap<String, Function> functions;
    public final boolean isPubic;
    public final String name;
    public SCPPParser.NamespaceDeclarationContext context = null;
    public String fileName = "";
    public int level = 0;

    public Namespace(String name, boolean isPublic) {
        this.name = name;
        this.isPubic = isPublic;

        variables = new LinkedHashMap<>();
        functions = new LinkedHashMap<>();
    }

    public void addUsedFunctions() {
        functions.values().stream().filter(f -> f.isUsed(0)).forEach(Function::add);
    }
}

