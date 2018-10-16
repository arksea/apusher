package net.arksea.pusher.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 *
 * Created by xiaohaixing on 2018/2/24.
 */
@Component
public class PushTargetManagerState {
    @Autowired
    PushTargetService pushTargetService;

    @Value("${push.pushTarget.autoCleanDays}")
    int pushTargetAutoCleanDays;

    @Value("${push.pushTarget.autoCleanPeriodMinutes}")
    int pushTargetAutoCleanPeriodMinutes;

    //删除pushTarget时是否按分区删除
    //当为false时，将在每天夜间执行一次批量删除
    //当为true时，将每5分钟执行一个分区的删除
    //一般应设置为false，除非pushTarget表做了批量导入，所有记录的过期时间非常集中
    @Value("${push.pushTarget.deleteByPartition}")
    boolean pushTargetDeleteByPartition;
}
