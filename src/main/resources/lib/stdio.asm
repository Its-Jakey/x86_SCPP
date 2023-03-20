

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
