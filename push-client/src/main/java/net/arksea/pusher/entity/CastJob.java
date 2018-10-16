package net.arksea.pusher.entity;

import net.arksea.pusher.CastType;
import org.hibernate.validator.constraints.NotBlank;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 *
 * Created by xiaohaixing on 2017/11/3.
 */
@Entity
@Table(name = "w2_cast_job",
    indexes = {@Index(columnList = "product"),
               @Index(columnList = "startTime"),
               @Index(columnList = "finishedTime")})
public class CastJob implements Serializable {
    private Long id;
    private String description; //任务描述
    private String product;     //产品ID或者与子产品ID的组合
    private String castTarget;  //推送目标,根据castType有不同的组合
    private CastType castType;  //推送类型，决定如何获取Payload
    private String testTarget; //测试推送目标，当配置了此字段时，将只有这些用户会发生实际推送，其他用户都会被跳过（但会参与成功数计数）
    private boolean enabled;
    private boolean running;   //任务是否正在运行
    private String userFilter; //json格式的用户过滤信息 {"minVer":"","maxVer":"","channels":"","script":""},
                               //srcipt支持groovy脚本, 脚本上下文可用变量：
                               //pushTarge.userInfo解析为Json对象后绑定为变量"info"，
                               //pushTartarget绑定为变量"target"
                               //userFilter解析为Json对象后绑定为变量"filter"
    private String payloadType;//用户自行定义，可作为查询条件，查询指定type类型的任务，同时对于Timer类型的推送，用此字段查询相关定时器表
    private String payload;    //静态推送内容，部分推送类型是通过接口动态获取，此时这里的值功能未定义，默认填空串
    private Timestamp startTime; //推送任务开始时间
    private Timestamp expiredTime; //推送任务过期时间
    private Timestamp finishedTime;//推送任务完成时间
    private Integer allCount;      //任务当前推送总数
    private Integer succeedCount;  //任务当前成功推送数
    private int failedCount;       //任务当前推送失败数
    private int retryCount;        //任务当前推送重试数
    private int lastPartition;     //任务当前处理到的分区
    private String lastUserId;     //任务最后完成推送的用户ID（成功或失败都有可能）

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
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

    @Column(length = 128, nullable = false)
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Column(length = 1024, nullable = false)
    public String getCastTarget() {
        return castTarget;
    }

    public void setCastTarget(String castTarget) {
        this.castTarget = castTarget;
    }

    @Column(length = 16, nullable = false)
    @Enumerated(EnumType.STRING)
    public CastType getCastType() {
        return castType;
    }

    public void setCastType(CastType castType) {
        this.castType = castType;
    }

    @Column(length = 1024, nullable = false)
    public String getTestTarget() {
        return testTarget;
    }

    public void setTestTarget(String testTarget) {
        this.testTarget = testTarget;
    }

    @Column(columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Column(columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    @Column(nullable = false)
    public Timestamp getStartTime() {
        return startTime;
    }

    public void setStartTime(Timestamp startTime) {
        this.startTime = startTime;
    }

    @Column(nullable = false)
    public Timestamp getExpiredTime() {
        return expiredTime;
    }

    public void setExpiredTime(Timestamp expiredTime) {
        this.expiredTime = expiredTime;
    }

    @Column
    public Timestamp getFinishedTime() {
        return finishedTime;
    }

    public void setFinishedTime(Timestamp finishedTime) {
        this.finishedTime = finishedTime;
    }

    @Column
    public Integer getAllCount() {
        return allCount;
    }

    public void setAllCount(Integer allCount) {
        this.allCount = allCount;
    }

    @Column
    public Integer getSucceedCount() {
        return succeedCount;
    }

    public void setSucceedCount(Integer succeedCount) {
        this.succeedCount = succeedCount;
    }

    @Column
    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    @Column
    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    @Column(length = 16, nullable = false)
    public String getPayloadType() {
        return payloadType;
    }

    public void setPayloadType(String payloadType) {
        this.payloadType = payloadType;
    }

    @Column(length = 1024, nullable = false)
    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    @Column(nullable = false)
    public int getLastPartition() {
        return lastPartition;
    }

    public void setLastPartition(int lastPartition) {
        this.lastPartition = lastPartition;
    }

    @Column(length = 48)
    public String getLastUserId() {
        return lastUserId;
    }

    public void setLastUserId(String lastUserId) {
        this.lastUserId = lastUserId;
    }

    @Column(length = 1024)
    public String getUserFilter() {
        return userFilter;
    }

    public void setUserFilter(String userFilter) {
        this.userFilter = userFilter;
    }
}
