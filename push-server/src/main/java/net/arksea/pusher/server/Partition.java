package net.arksea.pusher.server;

import net.arksea.pusher.entity.PushTarget;

/**
 * 分区号生成，同一个用户分区的数据必须被保存到一张表里
 * Created by xiaohaixing on 2017/7/5.
 */
public final class Partition {
    public final static int MAX_TABLE_PARTITION = 32;
    public final static int MAX_USER_PARTITION = MAX_TABLE_PARTITION*32;
    private Partition() {}
    public static int makeTablePartition(final PushTarget pd) {
        return makeTablePartition(pd.getUserId());
    }
    public static int makeTablePartition(String uid) {
        return getTablePartition(makeUserPartition(uid));
    }
    public static int makeUserPartition(final PushTarget pd) {
        return makeUserPartition(pd.getUserId());
    }
    public static int makeUserPartition(String uid) {
        return Math.abs(uid.hashCode()) % MAX_USER_PARTITION;
    }
    public static int getTablePartition(int userPartition) {
        return userPartition % MAX_TABLE_PARTITION;
    }
}
