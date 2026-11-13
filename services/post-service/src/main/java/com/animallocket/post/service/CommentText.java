package com.animallocket.post.service;

/** Chuẩn hoá nội dung bình luận trước khi kiểm tra độ dài và lưu. */
public final class CommentText {
    public static final int MAX_LENGTH = 300;

    private CommentText() {
    }

    /**
     * Bỏ ký tự điều khiển/định dạng (Unicode Cc, Cf) trừ xuống dòng, đổi tab thành dấu cách, gộp hơn hai dòng
     * trống liên tiếp thành một dòng trống, rồi cắt khoảng trắng hai đầu. Null được coi là chuỗi rỗng.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        raw.codePoints().forEach(cp -> {
            if (cp == '\n') {
                sb.append('\n');
            } else if (cp == '\t') {
                sb.append(' ');
            } else {
                int type = Character.getType(cp);
                if (type != Character.CONTROL && type != Character.FORMAT) {
                    sb.appendCodePoint(cp);
                }
            }
        });
        return sb.toString().replaceAll("\n{3,}", "\n\n").strip();
    }
}
