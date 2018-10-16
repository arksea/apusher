package net.arksea.pusher.server.cast;

import akka.actor.ActorRef;
import net.arksea.pusher.PushEvent;
import net.arksea.pusher.IPushStatusListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 *
 * Created by xiaohaixing on 2017/12/13.
 */
public class PushStatusListener  implements IPushStatusListener {
    private final static Logger logger = LogManager.getLogger(PushStatusListener.class);
    private final ActorRef jobActor;
    private final JobResources beans;
    public PushStatusListener(ActorRef jobActor, JobResources beans) {
        this.jobActor = jobActor;
        this.beans = beans;
    }

    @Override
    public void onSucceed(PushEvent event) {
        jobActor.tell(new CastJobActor.PushSucceed(event), ActorRef.noSender());
    }

    @Override
    public void onFailed(int status, Object reasonObj, PushEvent event) {
        if (reasonObj instanceof Throwable) {
            if (reasonObj instanceof IOException) {
                logger.debug("push failed: eventId={},topic={}", event.id, event.topic, reasonObj);
            } else {
                logger.warn("push failed: eventId={},topic={}", event.id, event.topic, reasonObj);
            }
            jobActor.tell(new CastJobActor.PushFailed(event), ActorRef.noSender());
        } else {
            String reason = reasonObj.toString();
            if (status == -1 || status == 500 || status == 503) {
                logger.warn("apns push failed: status={},reason={},eventId={},topic={}", status, reason, event.id, event.topic);
                jobActor.tell(new CastJobActor.PushFailed(event), ActorRef.noSender());
            } else {
                switch (reason) {
                    case "DeviceTokenNotForTopic":
                    case "BadDeviceToken":
                    case "Unregistered":
                    case "ExpiredProviderToken":
                    case "InvalidProviderToken":
                    case "MissingProviderToken":
                    case "TooManyProviderTokenUpdates": //The provider token is being updated too often.
                    case "TooManyRequests":  //Too many requests were made consecutively to the same device token.
                        //这些状态表明是用户状态异常造成的推送失败，不做推送成功与失败计数
                        //并将tokenActive设置成false，下次不再向他推送
                        try {
                            jobActor.tell(new CastJobActor.PushInvalid(event), ActorRef.noSender());
                            this.beans.pushTargetService.updateTokenStatus(event.token, false);
                            logger.debug("apns push failed: status={},reason={},eventId={},topic={},token={}", status, reason, event.id, event.topic,event.token);
                        } catch (Exception ex) {
                            logger.warn("Update token status failed: {}", event.token, ex);
                        }
                        break;
                    case "InternalServerError":
                    case "ServiceUnavailable":
                    case "Shutdown":
                    default:
                        logger.warn("apns push failed: status={},reason={},eventId={},topic={}", status, reason, event.id, event.topic);
                        jobActor.tell(new CastJobActor.PushFailed(event), ActorRef.noSender());
                        break;
                }
            }
        }
    }
}
