package net.arksea.pusher.sys;

import akka.actor.ActorSystem;
import net.arksea.httpclient.HttpClientHelper;
import net.arksea.httpclient.asker.FuturedHttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 *
 * Created by xiaohaixing on 2017/1/31.
 */
@Component
public class HttpRequestFactory {
    @Autowired
    ActorSystem system;

    @Value("${httpclient.connectTimeout}")
    private int connectTimeout;
    @Value("${httpclient.socketTimeout}")
    private int socketTimeout;
    @Value("${httpclient.maxConnTotal}")
    private int maxConnTotal;
    @Value("${httpclient.maxConnPerRoute}")
    private int maxConnPerRoute;


    @Bean(name = "futuredHttpClient")
    public FuturedHttpClient createProxyHttpRequester() {
        final RequestConfig config = RequestConfig.custom().setConnectTimeout(connectTimeout).setSocketTimeout(socketTimeout).build();
        final HttpAsyncClientBuilder builder = HttpAsyncClients.custom()
            .setDefaultRequestConfig(config)
            .setMaxConnTotal(maxConnTotal)
            .setMaxConnPerRoute(maxConnPerRoute)
            .setKeepAliveStrategy(HttpClientHelper.createKeepAliveStrategy(300));
        return new FuturedHttpClient(10, system, "defaultHttpClientAsker", builder);
    }
}
