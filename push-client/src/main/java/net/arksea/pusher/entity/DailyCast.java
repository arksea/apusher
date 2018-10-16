package net.arksea.pusher.entity;

import org.hibernate.validator.constraints.NotBlank;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 每日定时推送表
 * Created by xiaohaixing on 2018/2/2.
 */
@Entity
@Table(name = "w2_daily_cast",
    uniqueConstraints = @UniqueConstraint(columnNames = {"product","payloadType"}))
public class DailyCast implements Serializable {
    private Long id;
    private String product;             //产品ID或者与子产品ID的组合
    private int minuteOfDay;            //当天0点开始的分钟数，
    private boolean enabled;            //定时器是否开启
    private String days;                //一个星期中哪些天需要推送，例如：  1,2,3,4,5表示周一到周五推送，周六周天不推送
    private String payloadType;         //用户自行定义，可作为查询条件，查询指定type类型的任务
    private String payloadUrl;          //用于获取推送内容的接口URL,可以包含推送系统需要填写的参数，值留空，支持situs、situsGroup、userId及userInfo（json）对象中的字段
    private String payloadCacheKeys;    //逗号分隔的URL参数名，推送系统会以这里指出的key的值缓存返回的payload值，当参数值都相同时将使用缓存的结果
                                        //可选的值为用户对应的PushTarget中的situs、situsGroup、userId及userInfo（json）对象中的字段
                                        //缓存的目的是快推送速度，否则每次都通过url获取，
                                        //在用户量大时会变得很慢。比如每次请求10ms，当需要推送100万用户时
                                        //需要在请求payload上花费近3个小时，
                                        //支持situs，situsGroup及user_info中的字段
    private String description;         //任务描述
    private String testTarget;          //测试推送目标，当配置了此字段时，将只有这些用户会发生实际推送，其他用户都会被跳过（但会参与成功数计数）
    private Timestamp lastCreated;      //最后一次生成的CastJob的日期
    private String userFilter;          //json格式的用户过滤信息 {"minVer":"","maxVer":"","channels":"","script":""},

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

    @Column(columnDefinition = "SMALLINT NOT NULL")
    public int getMinuteOfDay() {
        return minuteOfDay;
    }

    public void setMinuteOfDay(int minuteOfDay) {
        this.minuteOfDay = minuteOfDay;
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

    @Column(length = 128, nullable = false)
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Column(length = 1024, nullable = false)
    public String getTestTarget() {
        return testTarget;
    }

    public void setTestTarget(String testTarget) {
        this.testTarget = testTarget;
    }

    @Column
    public Timestamp getLastCreated() {
        return lastCreated;
    }

    public void setLastCreated(Timestamp lastCreated) {
        this.lastCreated = lastCreated;
    }

    @Column(length = 1024)
    public String getUserFilter() {
        return userFilter;
    }

    public void setUserFilter(String userFilter) {
        this.userFilter = userFilter;
    }
}
