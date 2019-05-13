package net.arksea.pusher.sys;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

/**
 *
 * Created by xiaohaixing on 2017/5/18.
 */
public class HttpClientHelper {
    private static Logger logger = LogManager.getLogger(HttpClientHelper.class);
    public static String readBody(HttpRequestBase request, HttpResponse response) throws IOException {
        HttpEntity entity = response.getEntity();
        if (entity == null) {
            request.abort();
            logger.debug("Http Respond(status={}): ",response.getStatusLine().getStatusCode());
            return "";
        } else {
            InputStream in = entity.getContent();
            InputStreamReader reader = null;
            try {
                final StringBuilder sb = new StringBuilder();
                Header h1 = response.getLastHeader("Content-Encoding");
                if (h1 != null && "gzip".equals(h1.getValue())) {
                    reader = new InputStreamReader(
                        new GZIPInputStream(in), "UTF-8");
                } else {
                    reader = new InputStreamReader(in, "UTF-8");
                }
                char[] cbuf = new char[128];
                int len;
                while ((len = reader.read(cbuf)) > -1) {
                    sb.append(cbuf, 0, len);
                }
                String data = sb.toString();
                logger.debug("Http Respond(status={}): {}", response.getStatusLine().getStatusCode(), data);
                return data;
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception ex) {
                        logger.debug("close stream failed", ex);
                    }
                } else if (in != null) {
                    try {
                        in.close();
                    } catch (Exception ex) {
                        logger.debug("close stream failed", ex);
                    }
                }
            }
        }
    }

    public static ConnectionKeepAliveStrategy createKeepAliveStrategy(int aliveSeconds) {
        return new ConnectionKeepAliveStrategy() {
            @Override
            public long getKeepAliveDuration(final HttpResponse response, final HttpContext context) {
                Args.notNull(response, "HTTP response");
                final BasicHeaderElementIterator it = new BasicHeaderElementIterator(response.headerIterator(HTTP.CONN_KEEP_ALIVE));
                while (it.hasNext()) {
                    final HeaderElement he = it.nextElement();
                    final String param = he.getName();
                    final String value = he.getValue();
                    if (value != null && param.equalsIgnoreCase("timeout")) {
                        try {
                            return Long.parseLong(value) * 1000;
                        } catch (final NumberFormatException ignore) {
                            return aliveSeconds * 1000;
                        }
                    }
                }
                return aliveSeconds * 1000;
            }
        };
    }
}
