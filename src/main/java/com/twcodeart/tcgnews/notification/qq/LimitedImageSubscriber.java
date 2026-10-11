package com.twcodeart.tcgnews.notification.qq;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

final class LimitedImageSubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final int limit;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private Flow.Subscription subscription;
    LimitedImageSubscriber(int limit) { this.limit = limit; }
    @Override public CompletionStage<byte[]> getBody() { return result; }
    @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
    @Override public void onNext(List<ByteBuffer> buffers) {
        for (var buffer : buffers) {
            if (buffer.remaining() > limit - bytes.size()) {
                subscription.cancel(); result.completeExceptionally(new java.io.IOException("OCR image exceeds byte limit")); return;
            }
            byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
        }
        subscription.request(1);
    }
    @Override public void onError(Throwable failure) { result.completeExceptionally(failure); }
    @Override public void onComplete() { result.complete(bytes.toByteArray()); }
}
