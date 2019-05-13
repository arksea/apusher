package net.arksea.pusher;

/**
 *
 * Created by xiaohaixing on 2018/10/25.
 */
public interface IConnectionStatusListener {
    void onSucceed(); //通讯请求成功时调用，用于清除通讯失败计数
    void onFailed();  //通讯请求失败时调用，将进行次数累计，累计多次失败将导致重连
    void reconnect(); //确认的连接失效可以调用此方法重建连接
    void connected(Object session); //连接成功后调用此方法
}
