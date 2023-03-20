#include <stdio.sc>

public namespace test uses (stdio) {

    public func main() {
        for (i from 0 to 10)
            prints("Hello World!\n");
    }
}