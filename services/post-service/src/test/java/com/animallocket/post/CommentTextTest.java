package com.animallocket.post;

import com.animallocket.post.service.CommentText;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommentTextTest {
    @Test
    void removesControlAndFormatCharacters() {
        assertEquals("abc", CommentText.normalize("a\u0000b\u0007c"));
        assertEquals("abc", CommentText.normalize("a\u200Bb\u202Ec")); // zero-width space, bidi override
        assertEquals("ab", CommentText.normalize("a\u0085b"));
    }

    @Test
    void tabBecomesSpaceAndNewlinesAreKept() {
        assertEquals("a b", CommentText.normalize("a\tb"));
        assertEquals("dòng 1\ndòng 2", CommentText.normalize("dòng 1\r\ndòng 2"));
    }

    @Test
    void collapsesMoreThanTwoConsecutiveNewlines() {
        assertEquals("a\n\nb", CommentText.normalize("a\n\n\n\n\nb"));
        assertEquals("a\n\nb", CommentText.normalize("a\n\nb"));
        assertEquals("a\n\nb", CommentText.normalize("a\n\u0000\n\n\nb"));
    }

    @Test
    void stripsOuterWhitespace() {
        assertEquals("xin chào", CommentText.normalize("  \n\t xin chào \n  "));
    }

    @Test
    void keepsVietnameseAndEmoji() {
        assertEquals("đẹp quá 😍", CommentText.normalize("đẹp quá 😍"));
    }

    @Test
    void nullAndOnlyInvisibleBecomeEmpty() {
        assertEquals("", CommentText.normalize(null));
        assertEquals("", CommentText.normalize("\u200B\u0000\n \t"));
    }
}
