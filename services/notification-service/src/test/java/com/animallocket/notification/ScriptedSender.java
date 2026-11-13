package com.animallocket.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** PushSender giả: trả lần lượt các kết quả đã kịch bản hoá, lặp lại kết quả cuối khi hết. */
class ScriptedSender implements PushSender {
    final List<Result> script;
    final List<String> sentTo = new ArrayList<>();
    int calls;

    ScriptedSender(Result... script) {
        this.script = List.of(script);
    }

    @Override
    public Result send(String token, String title, String body, Map<String, String> data) {
        sentTo.add(token);
        return script.get(Math.min(calls++, script.size() - 1));
    }
}
