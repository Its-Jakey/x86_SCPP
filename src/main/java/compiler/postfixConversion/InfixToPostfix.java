package compiler.postfixConversion;

import antlr.SCPPParser;
import compiler.Compiler;

import java.util.*;

import static compiler.Compiler.appendLine;
import static compiler.Evaluators.evaluateValue;

public class InfixToPostfix {
    private static int precedence(ValueOrOperator op){
        if (op.operator() == null)
            return -1;
        String x = op.operator();

        return switch (x) {
            case "+", "-" -> 1;
            case "..", "*", "/", "%" -> 2;
            case "&", "|", "^" -> 3;
            case "<", ">", "<=", ">=", "==", "!=", "<<", ">>" -> 4;
            case "&&", "||" -> 5;
            default -> -1;
        };
    }

    public static List<ValueOrOperator> infixToPostfix(List<ValueOrOperator> input){
        Stack<ValueOrOperator> stk = new Stack<>();
        List<ValueOrOperator> ret = new ArrayList<>();

        for (ValueOrOperator x : input) {
            if (x.value() == null && x.operator() == null)
                continue;

            if (x.value() != null)
                ret.add(x);
            else if (x.operator().equals("("))
                stk.push(x);
            else if (x.operator().equals(")")) {

                while (!stk.isEmpty() && stk.peek().operator() != null && !stk.peek().operator().equals("("))
                    ret.add(stk.pop());
                if (!stk.isEmpty())
                    stk.pop();
            } else {
                while (!stk.isEmpty() && precedence(stk.peek()) >= precedence(x))
                    ret.add(stk.pop());
                stk.push(x);
            }
        }
        while(!stk.isEmpty())
            ret.add(stk.pop());
        return ret;
    }

    public static final Map<String, String> operatorInstructions = new HashMap<>(Map.of(
            "+", "add",
            "-", "sub",
            "*", "mul",
            "/", "div",
            "&", "and",
            "|", "or",
            "^", "xor",
            ">>", "shr",
            "<<", "shl",
            ">", "setg"
    ));
    static {
        operatorInstructions.putAll(Map.of(
                "<", "setl",
                ">=", "setge",
                "<=", "setle",
                "==", "sete",
                "!=", "setne",
                "&&", "and",
                "||", "or"));
    }

    public static void evaluatePostfix(List<ValueOrOperator> postfix, String register) {
        for (ValueOrOperator op : postfix) {
            if (op.value() == null) { // Operator
                String operator = op.operator();
                appendLine("xor eax, eax");

                switch (operator) {
                    case "+", "-", "*", "&", "|", "^", ">>", "<<" -> {
                        String instruction = operatorInstructions.get(operator);
                        appendLine("pop ebx");
                        appendLine("pop eax");
                        appendLine(instruction + " eax, ebx");
                    }
                    case "/", "%" -> {
                        appendLine("pop ebx");
                        appendLine("pop eax");
                        appendLine("cdq");
                        appendLine("idiv ebx");
                    }
                    case "==" -> {
                        appendLine("pop ebx");
                        appendLine("pop eax");
                        appendLine("cmp eax, ebx");
                        appendLine("mov eax, 0");
                        appendLine("sete al");
                    }
                    case "!=" -> {
                        appendLine("pop ebx");
                        appendLine("pop eax");
                        appendLine("cmp eax, ebx");
                        appendLine("mov eax, 0");
                        appendLine("setne al");
                    }
                    case ">" -> {
                        appendLine("pop ebx");
                        appendLine("pop eax");
                        appendLine("cmp eax, ebx");
                        appendLine("mov eax, 0");
                        appendLine("setg al");
                    }
                    case "<" -> {
                        appendLine("pop ebx");
                        appendLine("pop eax");
                        appendLine("cmp eax, ebx");
                        appendLine("mov eax, 0");
                        appendLine("setl al");
                    }
                    case "&&" -> {
                        appendLine("pop ebx");
                        appendLine("pop eax");
                        appendLine("and eax, ebx");
                        appendLine("mov ebx, 0");
                        appendLine("cmp eax, ebx");
                        appendLine("mov eax, 0");
                        appendLine("setne al");
                    }
                    case "||" -> {
                        appendLine("pop ebx");
                        appendLine("pop eax");
                        appendLine("or eax, ebx");
                        appendLine("mov ebx, 0");
                        appendLine("cmp eax, ebx");
                        appendLine("mov eax, 0");
                        appendLine("setne al");
                    }
                    default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
                }
            } else { // Value
                evaluateValue(op.value());
            }
            appendLine("push eax");
        }
        appendLine("pop " + register);
    }

    public static void evaluatePostfix(List<ValueOrOperator> postfix) {
        evaluatePostfix(postfix, "eax");
    }

    public static void addExpressionToList(SCPPParser.ExpressionContext expression, List<ValueOrOperator> ret) {
        if (expression.OPERATOR() != null) {
            addExpressionToList(expression.expression(0), ret);
            ret.add(new ValueOrOperator(null, expression.OPERATOR().getText()));
            addExpressionToList(expression.expression(1), ret);
        } else if (expression.value() != null)
            ret.add(new ValueOrOperator(expression.value(), null));
        else {
            ret.add(new ValueOrOperator(null, "("));
            addExpressionToList(expression.expression(0), ret);
            ret.add(new ValueOrOperator(null, ")"));
        }
    }
}
