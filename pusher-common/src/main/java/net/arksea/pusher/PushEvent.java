package net.arksea.pusher;

import java.io.Serializable;

/**
 *
 * Created by xiaohaixing on 2016/8/11.
 */
public class PushEvent implements Serializable {
    public final String id;
    public final String topic;
    public final String payload;
    public final String payloadType;
    public final String[] tokens;
    public final long expiredTime;
    public final long createTime;
    public final boolean testEvent;
    private int retryCount = 0;

    public PushEvent(String id, final String topic, final String[] tokens, final String payload, String payloadType, final long expiredTime) {
        this.id = id;
        this.topic = topic;
        this.payload = payload;
        this.payloadType = payloadType;
        this.tokens = tokens;
        this.expiredTime = expiredTime;
        this.testEvent = false;
        this.createTime = System.currentTimeMillis();
    }

    public PushEvent(String id, final String topic, final String[] tokens, final String payload, String payloadType, final long expiredTime, final boolean testEvent) {
        this.id = id;
        this.topic = topic;
        this.payload = payload;
        this.payloadType = payloadType;
        this.tokens = tokens;
        this.expiredTime = expiredTime;
        this.testEvent = testEvent;
        this.createTime = System.currentTimeMillis();
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void incRetryCount() {
        this.retryCount++;
    }

    public void decRetryCount() {
        if (this.retryCount > 0) {
            this.retryCount--;
        }
    }
}
