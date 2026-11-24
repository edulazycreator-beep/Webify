package javax.lang.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Shim untuk runtime Android (ART) yang tidak menyertakan modul java.compiler.
 *
 * ecj (org.eclipse.jdt.internal.compiler.batch.FileSystem) memiliki blok static:
 * {@code SourceVersion.valueOf("RELEASE_12")} yang hanya menangkap IllegalArgumentException.
 * Tanpa kelas ini muncul NoClassDefFoundError saat kompilasi on-device.
 * Enum sengaja berhenti di RELEASE_8 sehingga ecj menganggap runtime "bukan JRE 12+"
 * dan tidak mencoba membuka jrt-fs (tidak ada di Android).
 */
public enum SourceVersion {
    RELEASE_0, RELEASE_1, RELEASE_2, RELEASE_3, RELEASE_4, RELEASE_5, RELEASE_6, RELEASE_7, RELEASE_8;

    private static final Set<String> KEYWORDS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "abstract", "continue", "for", "new", "switch", "assert", "default", "if", "package", "synchronized",
            "boolean", "do", "goto", "private", "this", "break", "double", "implements", "protected", "throw",
            "byte", "else", "import", "public", "throws", "case", "enum", "instanceof", "return", "transient",
            "catch", "extends", "int", "short", "try", "char", "final", "interface", "static", "void",
            "class", "finally", "long", "strictfp", "volatile", "const", "float", "native", "super", "while",
            "_", "true", "false", "null")));

    public static SourceVersion latest() {
        return RELEASE_8;
    }

    public static SourceVersion latestSupported() {
        return RELEASE_8;
    }

    public static boolean isIdentifier(CharSequence name) {
        String id = name.toString();
        if (id.length() == 0) return false;
        int cp = id.codePointAt(0);
        if (!Character.isJavaIdentifierStart(cp)) return false;
        for (int i = Character.charCount(cp); i < id.length(); i += Character.charCount(cp)) {
            cp = id.codePointAt(i);
            if (!Character.isJavaIdentifierPart(cp)) return false;
        }
        return true;
    }

    public static boolean isKeyword(CharSequence s) {
        return KEYWORDS.contains(s.toString());
    }

    public static boolean isKeyword(CharSequence s, SourceVersion version) {
        return isKeyword(s);
    }

    public static boolean isName(CharSequence name) {
        return isName(name, latest());
    }

    public static boolean isName(CharSequence name, SourceVersion version) {
        String id = name.toString();
        for (String part : id.split("\\.", -1)) {
            if (!isIdentifier(part) || isKeyword(part)) return false;
        }
        return true;
    }
}
