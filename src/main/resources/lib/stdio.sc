#include <stdio.asm>

public namespace stdio {
    public func strlen(str) {
        _asm_("push %1\ncall strlen\nadd esp, 4", str);
    }

    public func prints(str) {
        _asm_("push %1\ncall strlen\nadd esp, 4\nmov edx, eax\nmov eax, 4\nmov ebx, 1\nmov ecx, %1\nint 0x80", str);
    }
}