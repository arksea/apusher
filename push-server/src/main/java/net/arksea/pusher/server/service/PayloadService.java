package net.arksea.pusher.server.service;

import akka.dispatch.Futures;
import akka.dispatch.Mapper;
import net.arksea.pusher.entity.PushTarget;
import net.arksea.httpclient.asker.FuturedHttpClient;
import net.arksea.httpclient.asker.HttpAsk;
import net.arksea.httpclient.asker.HttpResult;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 *
 * Created by xiaohaixing on 2018/2/23.
 */
@Component
public class PayloadService {
    private static Logger logger = LogManager.getLogger(PayloadService.class);
    @Autowired
    FuturedHttpClient futuredHttpClient;

    public Future<List<PushTarget>> sequence(List<Future<PushTarget>> targets) {
        ExecutionContext d = futuredHttpClient.system.dispatcher();
        return Futures.sequence(targets, d).map(new Mapper<Iterable<PushTarget>, List<PushTarget>>() {
            @Override
            public List<PushTarget> apply(Iterable<PushTarget> p) {
                List list = new LinkedList();
                p.forEach(it -> {
                    if (it != null && !StringUtils.isEmpty(it.getPayload())) {
                        list.add(it);
                    }
                });
                return list;
            }
        },d);
    }

    public Future<PushTarget> fillPayload(Future<PushTarget> targetFuture, String payloadUrl, String cacheKeyNames, Map<String,String> payloadCache) {
        return targetFuture.flatMap(new Mapper<PushTarget,Future<PushTarget>>() {
            public Future<PushTarget> apply(PushTarget target) {
                return fillPayload(target, payloadUrl, cacheKeyNames, payloadCache);
            }
        },futuredHttpClient.system.dispatcher());
    }

    public Future<PushTarget> fillPayload(PushTarget target, String payloadUrl, String cacheKeyNames, Map<String,String> payloadCache) {
        try {
            if (target == null) {
                return Futures.successful(null);
            }
            final String cacheKey;
            if (StringUtils.isEmpty(cacheKeyNames)) {
                cacheKey = "";
            } else {
                cacheKey = getCacheKey(cacheKeyNames, target);
                if (StringUtils.isEmpty(cacheKey)) {
                    //有设置缓存key但target没有有效的key值则认为是无效target，不设置payload直接返回
                    return Futures.successful(target);
                } else {
                    String payload = payloadCache.get(cacheKey);
                    if (payload != null) {
                        target.setPayload(payload);
                        return Futures.successful(target);
                    }
                }
            }
            String url = fillUrlParams(payloadUrl, target);
            logger.trace("get payload from: {}",url);
            HttpGet get = new HttpGet(url);
            return futuredHttpClient.ask(new HttpAsk("request", get), 5000).map(
                new Mapper<HttpResult, PushTarget>() {
                    @Override
                    public PushTarget apply(HttpResult ret) {
                        if (ret.error == null) {
                            int code = ret.response.getStatusLine().getStatusCode();
                            if (code == 200) {
                                target.setPayload(ret.value);
                                if (!StringUtils.isEmpty(cacheKey)) {
                                    if (StringUtils.isEmpty(ret.value)) {
                                        payloadCache.put(cacheKey, "");
                                    } else {
                                        payloadCache.put(cacheKey, ret.value);
                                    }
                                }
                            }
                        }
                        return target;
                    }
                }, futuredHttpClient.system.dispatcher());
        } catch (Exception ex) {
            logger.warn("获取payload失败，payloadUrl={},cacheKeys={}",payloadUrl, cacheKeyNames, ex);
            return Futures.successful(target);
        }
    }

    /**
     * 填充URL中的空参数，
     * 例如 http://tq.ifjing.com/api/v1/push/payload/today?userId=&situsGroup=&type=1&name=
     * urserId和situsGroup将被填充，type参数因为已经有值保持不变，name参数因为不存在不做填充
     * @param url
     * @return
     */
    private String fillUrlParams(String url,PushTarget target) throws UnsupportedEncodingException {
        //解析URL，并填充参数
        String[] strs = StringUtils.split(url,'?');
        if (strs.length > 1) {
            List<NameValuePair> list = URLEncodedUtils.parse(strs[1], Charset.forName("UTF-8"));
            StringBuilder urlsb = new StringBuilder(strs[0]);
            urlsb.append('?');
            for (int i=0; i<list.size(); ++i) {
                NameValuePair pair = list.get(i);
                String n = pair.getName();
                String v = pair.getValue();
                if (i>0) {
                    urlsb.append('&');
                }
                urlsb.append(n).append('=');
                if (StringUtils.isEmpty(v)) {
                    switch(n) {
                        case "userId":
                            urlsb.append(URLEncoder.encode(target.getUserId(),"utf-8"));
                            break;
                        case "situs":
                            urlsb.append(URLEncoder.encode(target.getSitus(), "utf-8"));
                            break;
                        case "location":
                            urlsb.append(URLEncoder.encode(target.getLocation(), "utf-8"));
                            break;
                        case "situsGroup":
                            urlsb.append(URLEncoder.encode(target.getSitusGroup(), "utf-8"));
                            break;
                        default: //优先级D, todo: 支持target.getUserInfo()中的参数
                            break;
                    }
                } else {
                    urlsb.append(URLEncoder.encode(v, "utf-8"));
                }
            }
            return urlsb.toString();
        } else {
            return url;
        }
    }

    private String getCacheKey(String keyNames,PushTarget target) throws UnsupportedEncodingException {
        String[] strs = StringUtils.split(keyNames, ',');
        StringBuilder sb = new StringBuilder();
        for (String n : strs) {
            String value;
            switch(n) {
                case "situs":
                    value = target.getSitus();
                    break;
                case "situsGroup":
                    value = target.getSitusGroup();
                    break;
                case "userId": //不支持以userId作为key，缓存无意义，而且量太大
                    value = "";
                    break;
                default: //优先级 D, todo: 支持target.getUserInfo()中的参数
                    value = "";
                    break;
            }
            if (!StringUtils.isEmpty(value)) {
                sb.append(n).append('=')
                  .append(URLEncoder.encode(value, "utf-8"));
            }
        }
        return sb.toString();
    }
}
