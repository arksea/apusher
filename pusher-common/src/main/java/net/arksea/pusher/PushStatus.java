package net.arksea.pusher;

/**
 * 推送回执状态
 * Created by xiaohaixing on 2018/10/25.
 */
public enum PushStatus {
    PUSH_FAILD,   //推送失败
    PUSH_SUCCEED, //推送成功
    INVALID_TOKEN //无效的TOKEN
}
