section .data
    string3375: db `Hello World!\n`,0
    string3376: db `One!`,0
    string3377: db `Zero!`,0

section .text
    global _start
strlen_1336c:
    push ebp
    mov ebp, esp
    push dword [ebp+8]
    call strlen
    add esp, 4
    mov esp, ebp
    pop ebp
    ret

prints_1336f:
    push ebp
    mov ebp, esp
    push dword [ebp+8]
    call strlen
    add esp, 4
    mov edx, eax
    mov eax, 4
    mov ebx, 1
    mov ecx, dword [ebp+8]
    int 0x80
    mov esp, ebp
    pop ebp
    ret

main_03372:
    push ebp
    mov ebp, esp
    mov dword [ebp-4], 0
    mov dword [ebp-8], 10
    temp0:
        mov eax, dword [ebp-4]
        mov ebx, dword [ebp-8]
        cmp eax, ebx
        jge temp1
        sub esp, 12
        mov eax, string3375
        push eax
        call prints_1336f
        add esp, 4
        add esp, 12
        mov eax, dword [ebp-4]
        inc eax
        mov dword [ebp-4], eax
        jmp temp0
    temp1:
    mov eax, 1
    cmp eax, 0
    jle ifExit0
    sub esp, 12
    mov eax, string3376
    push eax
    call prints_1336f
    add esp, 4
    add esp, 12
    jmp elseExit0
    ifExit0:
    elseEnter0:
    sub esp, 12
    mov eax, string3377
    push eax
    call prints_1336f
    add esp, 4
    add esp, 12
    elseExit0:
    mov eax, 0
    cmp eax, 0
    jle ifExit1
    sub esp, 12
    mov eax, string3377
    push eax
    call prints_1336f
    add esp, 4
    add esp, 12
    jmp elseExit1
    ifExit1:
    elseEnter1:
    sub esp, 12
    mov eax, string3376
    push eax
    call prints_1336f
    add esp, 4
    add esp, 12
    elseExit1:
    mov eax, 0
    push eax
    mov eax, 1
    push eax
    xor eax, eax
    pop ebx
    pop eax
    cmp eax, ebx
    mov eax, 0
    setl al
    push eax
    pop eax
    cmp eax, 0
    jle ifExit2
    sub esp, 12
    mov eax, string3376
    push eax
    call prints_1336f
    add esp, 4
    add esp, 12
    jmp elseExit2
    ifExit2:
    elseEnter2:
    sub esp, 12
    mov eax, string3377
    push eax
    call prints_1336f
    add esp, 4
    add esp, 12
    elseExit2:
    mov eax, 1
    push eax
    mov eax, 1
    push eax
    xor eax, eax
    pop ebx
    pop eax
    cmp eax, ebx
    mov eax, 0
    setl al
    push eax
    pop eax
    cmp eax, 0
    jle ifExit3
    sub esp, 12
    mov eax, string3377
    push eax
    call prints_1336f
    add esp, 4
    add esp, 12
    jmp elseExit3
    ifExit3:
    elseEnter3:
    sub esp, 12
    mov eax, string3376
    push eax
    call prints_1336f
    add esp, 4
    add esp, 12
    elseExit3:
    mov eax, 1
    push eax
    mov eax, 0
    push eax
    xor eax, eax
    pop ebx
    pop eax
    cmp eax, ebx
    mov eax, 0
    sete al
    push eax
    pop eax
    cmp eax, 0
    jle ifExit4
    sub esp, 12
    mov eax, string3377
    push eax
    call prints_1336f
    add esp, 4
    add esp, 12
    jmp elseExit4
    ifExit4:
    elseEnter4:
    sub esp, 12
    mov eax, string3376
    push eax
    call prints_1336f
    add esp, 4
    add esp, 12
    elseExit4:
    mov eax, 1
    push eax
    mov eax, 1
    push eax
    xor eax, eax
    pop ebx
    pop eax
    cmp eax, ebx
    mov eax, 0
    sete al
    push eax
    pop eax
    cmp eax, 0
    jle ifExit5
    sub esp, 12
    mov eax, string3376
    push eax
    call prints_1336f
    add esp, 4
    add esp, 12
    jmp elseExit5
    ifExit5:
    elseEnter5:
    sub esp, 12
    mov eax, string3377
    push eax
    call prints_1336f
    add esp, 4
    add esp, 12
    elseExit5:
    mov esp, ebp
    pop ebp
    ret


_start:
    call main_03372
    mov eax, 1
    xor ebx, ebx
    int 0x80


strlen:
    mov eax, [esp+4]
    mov ecx, 0
    .loop:
        cmp byte [eax], 0
        je .end
        inc eax
        inc ecx
        jmp .loop
    .end:
        mov eax, ecx
        ret

