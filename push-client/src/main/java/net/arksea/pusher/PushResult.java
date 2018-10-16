package net.arksea.pusher;

import java.io.Serializable;

/**
 *
 * Created by xiaohaixing on 2017/11/6.
 */
public class PushResult<T> implements Serializable {
    public final int status;
    public final String message;
    public final T result;
    public PushResult(int status) {
        this.status = status;
        this.result = null;
        if (status == 0) {
            message = "succeed";
        } else {
            message = "failed";
        }
    }
    public PushResult(int status, T result) {
        this.status = status;
        this.result = result;
        if (status == 0) {
            message = "succeed";
        } else {
            message = "failed";
        }
    }
    public PushResult(int status, String msg, T result) {
        this.status = status;
        this.result = result;
        this.message = msg;
    }
}

