package compiler.memory;

public class Switch {
    public final Variable switchValue;
    public final String exitPoint;

    public Switch(Variable switchValue, String exitPoint) {
        this.switchValue = switchValue;
        this.exitPoint = exitPoint;
    }
}
