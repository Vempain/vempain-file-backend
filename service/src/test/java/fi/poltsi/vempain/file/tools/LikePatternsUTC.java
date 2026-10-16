package fi.poltsi.vempain.file.tools;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LikePatternsUTC {

    @Test
    void wildcardAndEscapeCharactersAreEscaped() {
        assertEquals("\\%", LikePatterns.escape("%"));
        assertEquals("\\_", LikePatterns.escape("_"));
        assertEquals("\\\\", LikePatterns.escape("\\"));
        assertEquals("50\\% off\\_now", LikePatterns.escape("50% off_now"));
        assertEquals("plain text", LikePatterns.escape("plain text"));
        assertEquals("", LikePatterns.escape(null));
    }

    @Test
    void containsAndPrefixWrapTheEscapedText() {
        assertEquals("%\\%%", LikePatterns.contains("%"));
        assertEquals("%sunset%", LikePatterns.contains("sunset"));
        assertEquals("%sunset%", LikePatterns.containsIgnoreCase("SunSet"));
        assertEquals("trips/2024\\_x%", LikePatterns.prefix("trips/2024_x"));
        assertEquals("%%", LikePatterns.contains(null));
    }

    @Test
    void overlongTextIsTruncated() {
        var text = "a".repeat(LikePatterns.MAX_TEXT_LENGTH + 50);
        var pattern = LikePatterns.contains(text);

        assertEquals(LikePatterns.MAX_TEXT_LENGTH + 2, pattern.length());
        assertTrue(pattern.startsWith("%a")
                   && pattern.endsWith("a%"));
    }

    @Test
    void tokenListsAreCappedAndNullTextIsAnEmptyPattern() {
        assertThat(LikePatterns.limitTokens(null)).isEmpty();
        var tokens = IntStream.range(0, 12)
            .mapToObj(Integer::toString)
            .toList();
        assertThat(LikePatterns.limitTokens(tokens)).hasSize(LikePatterns.MAX_TOKENS);
        assertThat(LikePatterns.limitTokens(List.of("a"))).containsExactly("a");
        assertThat(LikePatterns.containsIgnoreCase(null)).isEqualTo("%%");
        assertThat(LikePatterns.containsIgnoreCase("ÅÄÖ")).isEqualTo("%åäö%");
    }
}
