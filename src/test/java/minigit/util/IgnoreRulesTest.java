package minigit.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IgnoreRulesTest {

    private final IgnoreRules rules = IgnoreRules.parse("""
            # build output
            *.class
            build/
            /secret.txt
            docs/*.pdf
            logs/**/debug.log

            temp?.txt
            """);

    @Test
    void nameGlobsMatchAtAnyDepth() {
        assertTrue(rules.isIgnored("Main.class", false));
        assertTrue(rules.isIgnored("src/app/Main.class", false));
        assertFalse(rules.isIgnored("Main.java", false));
        assertTrue(rules.isIgnored("temp1.txt", false));
        assertFalse(rules.isIgnored("temp12.txt", false));
    }

    @Test
    void trailingSlashMatchesDirectoriesAndTheirContents() {
        assertTrue(rules.isIgnored("build", true));
        assertTrue(rules.isIgnored("build/out/app.jar", false));
        assertTrue(rules.isIgnored("module/build/x.txt", false));
        assertFalse(rules.isIgnored("build", false), "a file named build is not a directory");
    }

    @Test
    void patternsWithSlashAreAnchoredAtRoot() {
        assertTrue(rules.isIgnored("secret.txt", false));
        assertFalse(rules.isIgnored("config/secret.txt", false));
        assertTrue(rules.isIgnored("docs/guide.pdf", false));
        assertFalse(rules.isIgnored("docs/sub/guide.pdf", false));
        assertFalse(rules.isIgnored("other/docs/guide.pdf", false));
    }

    @Test
    void doubleStarCrossesFolders() {
        assertTrue(rules.isIgnored("logs/a/b/debug.log", false));
        assertFalse(rules.isIgnored("logs/a/b/info.log", false));
    }

    @Test
    void dotsAreLiteral() {
        assertFalse(IgnoreRules.parse("a.txt").isIgnored("abtxt", false));
    }
}
