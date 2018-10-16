package net.arksea.pusher.entity;

import org.hibernate.validator.constraints.NotBlank;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 每日推送，每（整）10分钟根据此表生成一个新任务，并插入CastJob表
 * Created by xiaohaixing on 2017/11/3.
 */
@Entity
@Table(name = "w2_user_daily_cast",
    uniqueConstraints = @UniqueConstraint(columnNames = {"product","payloadType"}))
public class UserDailyCast implements Serializable {
    private Long id;
    private String description;//任务描述
    private String product;    //产品ID或者与子产品ID的组合
    private String testTarget; //测试推送目标，当配置了此字段时，将只有这些用户会发生实际推送，其他用户都会被跳过（但会参与成功数计数）
    private boolean enabled;
    private Timestamp lastCreated;//最后一次生成的CastJob的时间
    private String userFilter; //json格式的用户过滤信息 {"minVer":"","maxVer":"","channels":"","script":""},
                               //srcipt支持groovy脚本, 脚本上下文可用变量：
                               //pushTarge.userInfo解析为Json对象后绑定为变量"info"，
                               //pushTartarget绑定为变量"target"
                               //userFilter解析为Json对象后绑定为变量"filter"
    private String payloadType;//用户自行定义，可作为查询条件，查询指定type类型的任务，同时对于Timer类型的推送，用此字段查询相关定时器表
    private String payload;    //静态推送内容，部分推送类型是通过接口动态获取，此时这里的值功能未定义，默认填空串

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

    @Column(length = 1024)
    public String getUserFilter() {
        return userFilter;
    }

    public void setUserFilter(String userFilter) {
        this.userFilter = userFilter;
    }

//    @Column(columnDefinition = "SMALLINT(4) NOT NULL")
//    public Integer getMinuteOfDay() {
//        return minuteOfDay;
//    }
//
//    public void setMinuteOfDay(int minuteOfDay) {
//        this.minuteOfDay = minuteOfDay;
//    }

    @Column
    public Timestamp getLastCreated() {
        return lastCreated;
    }

    public void setLastCreated(Timestamp lastCreated) {
        this.lastCreated = lastCreated;
    }
}
