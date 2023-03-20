package main;

import compiler.Compiler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

public class Main {
    private static String replaceExtension(String input, String extension) {
        return input.substring(0, input.lastIndexOf('/') + 1) + input.substring(input.lastIndexOf('/') + 1, input.lastIndexOf('.')) + extension;
    }
    public static void main(String[] args) throws IOException {
        String input = "test.sc";
        String asmName = "output/" + replaceExtension(input, ".asm");
        String oName = "output/" + replaceExtension(input, ".o");
        String binName = "output/" + replaceExtension(input, "");

        Files.writeString(Path.of(asmName), Compiler.compile(Path.of(new File(input).getAbsolutePath())));
        Files.writeString(Path.of("build.sh"), (
                """
                        clear
                        nasm -f elf32 -o %s %s
                        ld -m elf_i386 -o %s %s""").formatted(oName, asmName, binName, oName));
        Files.writeString(Path.of("run.sh"), "clear\n./" + binName);
    }
}
