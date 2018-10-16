package net.arksea.pusher.entity;

import org.hibernate.validator.constraints.NotBlank;

import javax.persistence.*;
import java.io.Serializable;

/**
 * 用户每日定时推送表
 * Created by xiaohaixing on 2018/2/2.
 * 此库表为可能包含数千万条数据的大表，
 * 所以为了优化索引查询性能所有字段都被定义为非NULL
 * 没有定义唯一性索引：product+userId+payloadType+timerId
 * 请格外注意程序对数据正确性的保障
 */
@Entity
@Table(name = "w2_user_daily_timer",
    //uniqueConstraints = @UniqueConstraint(columnNames = {"product,userId,payloadType,timerId"}),
    indexes = {@Index(columnList = "partitions"),
               @Index(columnList = "userId"),
               @Index(columnList = "minuteOfDay")})
public class UserDailyTimer implements Serializable {
    private Long id;
    private String product;             //产品ID或者与子产品ID的组合
    private String userId;              //用户ID或设备ID，也可以是账号与设备ID及其他信息的组合
    private int minuteOfDay;            //+8区当天0点开始的分钟数，
                                        //统一转换成同一个时区，是为了定时调度程序统一处理，
                                        //统一转换成+8区是因为维护人员在+8区，为了好维护好计算
    private Integer zoneOffset;         //用户所在时区，用于显示时有能力还原用户当地时间
    private Long timerId;               //用户定时器ID
    private boolean enabled;            //定时器是否开启
    private String days;                //一个星期中哪些天需要推送，例如：  1,2,3,4,5表示周一到周五推送，周六周天不推送
    private String payloadType;         //用户自行定义，可作为查询条件，查询指定type类型的任务
    private Integer partitions;         //用于优化库表查询性能的分区号, 分区方法由Partition类定义
                                        //定义为Integer而非int是为了防止没有设置值，导致默认为0引起的不均衡问题
    private String payloadUrl;          //用于获取推送内容的接口URL,可以包含推送系统需要填写的参数，值留空，支持situs、situsGroup、userId及userInfo（json）对象中的字段
    private String payloadCacheKeys;    //逗号分隔的URL参数名，推送系统会以这里指出的key的值缓存返回的payload值，当参数值都相同时将使用缓存的结果
                                        //可选的值为用户对应的PushTarget中的situs、situsGroup、userId及userInfo（json）对象中的字段
                                        //缓存的目的是快推送速度，否则每次都通过url获取，
                                        //在用户量大时会变得很慢。比如每次请求10ms，当需要提送100万用户时
                                        //需要在请求payload上花费近3个小时，
                                        //支持situs，situsGroup及user_info中的字段
    private boolean deleted;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @NotBlank
    @Column(length = 12, nullable = false)
    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    @NotBlank
    @Column(length = 64, nullable = false)
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Column(columnDefinition = "SMALLINT NOT NULL")
    public int getMinuteOfDay() {
        return minuteOfDay;
    }

    public void setMinuteOfDay(int minuteOfDay) {
        this.minuteOfDay = minuteOfDay;
    }

    @Column(columnDefinition = "TINYINT(1) NOT NULL DEFAULT 8")
    public Integer getZoneOffset() {
        return zoneOffset;
    }

    public void setZoneOffset(Integer zoneOffset) {
        this.zoneOffset = zoneOffset;
    }

    @Column(columnDefinition = "BIGINT(20) NOT NULL DEFAULT 0")
    public Long getTimerId() {
        return timerId;
    }

    public void setTimerId(Long timerId) {
        this.timerId = timerId;
    }

    @Column(columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Column(length = 16, nullable = false)
    public String getDays() {
        return days;
    }

    public void setDays(String days) {
        this.days = days;
    }

    @Column(length = 16, nullable = false)
    public String getPayloadType() {
        return payloadType;
    }

    public void setPayloadType(String payloadType) {
        this.payloadType = payloadType;
    }

    @Column(columnDefinition = "SMALLINT NOT NULL")
    public Integer getPartitions() {
        return partitions;
    }

    public void setPartitions(Integer partitions) {
        this.partitions = partitions;
    }

    @Column(length = 256, nullable = false)
    public String getPayloadUrl() {
        return payloadUrl;
    }

    public void setPayloadUrl(String payloadUrl) {
        this.payloadUrl = payloadUrl;
    }

    @Column(length = 64, nullable = false)
    public String getPayloadCacheKeys() {
        return payloadCacheKeys;
    }

    public void setPayloadCacheKeys(String payloadCacheKeys) {
        this.payloadCacheKeys = payloadCacheKeys;
    }

    public boolean isDeleted() {
        return deleted;
    }

    @Column(columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
