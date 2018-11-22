package net.arksea.pusher.entity;

import org.hibernate.validator.constraints.NotBlank;

import javax.persistence.*;
import java.sql.Timestamp;

/**
 * 推送目标设备表
 * Created by xiaohaixing on 2017/8/29.
 * 此库表为可能包含数千万条数据的大表，
 * 所以为了优化索引查询性能所有字段都被定义为非NULL
 * 没有定义唯一性索引： product+userId
 * 请格外注意程序对数据正确性的保障
 */
@Entity
@Table(name = "w2_push_target",indexes = {@Index(columnList = "partitions, userId"), //分区查询需要用userId排序，为防止Mysql做filesort操作，索引增加此字段
                                          @Index(columnList = "userId"),
                                          @Index(columnList = "token"),
                                          @Index(columnList = "situs"),
                                          @Index(columnList = "situsGroup"),
                                          @Index(columnList = "lastUpdate")})
public class PushTarget extends IdEntity implements Cloneable {
    private String product;             //产品ID或者与子产品ID的组合
    private String userId;              //用户ID或设备ID，也可以是账号与设备ID及其他信息的组合
    private String userInfo="";         //扩展信息1，通常用于推送的过滤选择，比如产品版本号
    private String token="";            //Apple的设备Token
    private boolean tokenActived;       //tocken的激活状态
    private String location="";         //定位信息，通常是经纬度或者由经纬度通过地图定位服务获取到的具体位置信息（比如街道）
    private String situs="";            //location所在的区域，通常是城市编码，或者某一区块的ID，由具体应用自行定义
    private String situsGroup="";       //区域分组，通常是省份，或者由小区块组成的大区块，由具体应用自行定义
    private Integer partitions;         //用于优化库表查询性能的分区号, 分区方法由Partition类定义

    // 定义为Integer而非int是为了防止没有设置值，导致默认为0引起的不均衡问题
    private String payload;             //对象额外携带字段：保存推送任务中目标定制化的推送内容
    private Timestamp lastUpdate;

    @Transient
    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    @NotBlank
    @Column(length = 10, nullable = false)
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

    @Column(columnDefinition = "VARCHAR(130) NOT NULL DEFAULT ''")
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    @Column(columnDefinition = "TINYINT(1) NOT NULL DEFAULT 1")
    public boolean isTokenActived() {
        return tokenActived;
    }

    public void setTokenActived(boolean tokenActived) {
        this.tokenActived = tokenActived;
    }

    @Column(columnDefinition = "VARCHAR(64) NOT NULL DEFAULT ''")
    public String getSitus() {
        return situs;
    }

    public void setSitus(String situs) {
        this.situs = situs;
    }

    @Column(columnDefinition = "VARCHAR(64) NOT NULL DEFAULT ''")
    public String getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(String userInfo) {
        this.userInfo = userInfo;
    }

    @Column(columnDefinition = "VARCHAR(16) NOT NULL DEFAULT ''")
    public String getSitusGroup() {
        return situsGroup;
    }

    public void setSitusGroup(String situsGroup) {
        this.situsGroup = situsGroup;
    }

    @Column(columnDefinition = "SMALLINT NOT NULL")
    public Integer getPartitions() {
        return partitions;
    }

    public void setPartitions(Integer partitions) {
        this.partitions = partitions;
    }

    @Column(columnDefinition = "VARCHAR(64) NOT NULL DEFAULT ''")
    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @Column(columnDefinition = "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    public Timestamp getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(Timestamp lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    @Override
    public PushTarget clone() throws CloneNotSupportedException {
        return (PushTarget) super.clone();
    }
}
