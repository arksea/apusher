package net.arksea.pusher;

/**
 * 推送类型，决定castTarget类型或格式，及如何获取Payload
 * Created by xiaohaixing on 2017/11/8.
 */
public enum CastType {
    UNIT,       //单播, castTarget字段: 逗号分隔的userId；payload取值： 常量，CastJob表当前记录payload字段
    BROAD,      //广播, castTarget字段: 未定义，默认设置为all；payload取值： 常量，CastJob表当前记录payload字段
    SITUS,      //按situs进行组播, castTarget字段: 逗号分隔的situs；payload取值： 常量，CastJob表payload字段
    SITUSGROUP, //按situsGroup进行组播, castTarget字段: 逗号分隔的situsGroup；payload取值： 常量，CastJob表payload字段
    USER_DAILY, //按每日定时时间广播, castTarget字段: 未定义，默认设置为all；payload取值： 根据payloadUrl字段动态获取用户定制payload
    USER_DAILY_TIMER,//按每日用户定时时间组播, castTarget: 从0点开始的分钟数；payload取值： 关联UserDailyTimer表所有相关userId，根据payloadUrl字段动态获取用户定制payload
    BATCH_BROAD, //批量推送， 一次对多个target推送同一个payload
    BATCH_DAILY,
    BATCH_SITUS,
    BATCH_SITUSGROUP
}
