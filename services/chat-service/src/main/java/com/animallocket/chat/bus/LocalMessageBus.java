package com.animallocket.chat.bus;

import java.util.function.BiConsumer;

public class LocalMessageBus implements MessageBus {
    private volatile BiConsumer<String, String> handler = (id, json) -> { };

    @Override
    public void publish(String publicId, String json) {
        handler.accept(publicId, json);
    }

    @Override
    public void subscribe(BiConsumer<String, String> deliverLocally) {
        this.handler = deliverLocally;
    }
}
