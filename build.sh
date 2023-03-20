nasm -f elf32 -o output/test.o output/test.asm
ld -m elf_i386 -o output/test output/test.o