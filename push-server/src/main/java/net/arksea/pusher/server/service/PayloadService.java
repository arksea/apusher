package net.arksea.pusher.server.service;

import net.arksea.pusher.entity.PushTarget;
import net.arksea.pusher.sys.HttpService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
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
    HttpService httpService;


    public void fillPayload(PushTarget target, String payloadUrl, String cacheKeyNames, Map<String,String> payloadCache) {
        try {
            if (target == null) {
                return;
            }
            final String cacheKey;
            if (StringUtils.isEmpty(cacheKeyNames)) {
                cacheKey = "";
            } else {
                cacheKey = getCacheKey(cacheKeyNames, target);
                if (StringUtils.isEmpty(cacheKey)) {
                    //有设置缓存key但target没有有效的key值则认为是无效target，不设置payload直接返回
                    return;
                } else {
                    String payload = payloadCache.get(cacheKey);
                    if (payload != null) {
                        target.setPayload(payload);
                        return;
                    }
                }
            }
            Pair<String,String> pair = fillUrlParams(payloadUrl, target);
            logger.trace("get payload from: {}",pair.getLeft());
            String payload;
            if (StringUtils.isEmpty(pair.getRight())) {
                payload = httpService.get(pair.getLeft());
            } else {
                payload = httpService.post(pair.getLeft(), pair.getRight());
            }
            target.setPayload(payload);
            if (!StringUtils.isEmpty(cacheKey)) {
                payloadCache.put(cacheKey, payload);
            }
        } catch (Exception ex) {
            logger.warn("request payload failed，payloadUrl={},cacheKeys={}",payloadUrl, cacheKeyNames, ex);
        }
    }

    /**
     * 填充URL中的空参数，
     * 例如 http://tq.ifjing.com/api/v1/push/payload/today?userId=&situsGroup=&type=1&name=
     * urserId和situsGroup将被填充，type参数因为已经有值保持不变，name参数因为不存在不做填充
     * @param url
     * @return
     */
    private Pair<String,String> fillUrlParams(String url, PushTarget target) throws UnsupportedEncodingException {
        //解析URL，并填充参数
        String[] strs = StringUtils.split(url,'?');
        if (strs.length > 1) {
            List<NameValuePair> list = URLEncodedUtils.parse(strs[1], Charset.forName("UTF-8"));
            StringBuilder urlsb = new StringBuilder(strs[0]);
            urlsb.append('?');
            String postBody = "";
            for (int i=0; i<list.size(); ++i) {
                NameValuePair pair = list.get(i);
                String n = pair.getName();
                String v = pair.getValue();
                if (i>0) {
                    urlsb.append('&');
                }
                urlsb.append(n).append('=');
                if ("_postUserInfo".equals(n)) {
                    if("true".equals(v)) {
                        postBody = target.getUserInfo();
                    }
                } else if (StringUtils.isEmpty(v)) {
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
                        case "partition":
                            urlsb.append(URLEncoder.encode(target.getPartitions().toString(), "utf-8"));
                            break;
                        default: //优先级D, todo: 支持target.getUserInfo()中的参数
                            break;
                    }
                } else {
                    urlsb.append(URLEncoder.encode(v, "utf-8"));
                }
            }
            return Pair.of(urlsb.toString(), postBody);
        } else {
            return Pair.of(url, "");
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
                case "partition":
                    value = target.getPartitions().toString();
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
