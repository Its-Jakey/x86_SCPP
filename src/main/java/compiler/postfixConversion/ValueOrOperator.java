package compiler.postfixConversion;

import java.util.Objects;
import antlr.SCPPParser;

public record ValueOrOperator(SCPPParser.ValueContext value, String operator) {}
