package net.arksea.pusher.tools;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import net.arksea.pusher.IConnectionStatusListener;
import net.arksea.pusher.IPushStatusListener;
import net.arksea.pusher.PushEvent;
import net.arksea.pusher.apns.PushClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.http2.api.Session;

import javax.net.ssl.KeyManagerFactory;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Base64;
import java.util.Properties;
import java.util.UUID;

/**
 *
 * Created by xiaohaixing on 2018/11/5.
 */
public class ApnsService {
    Logger logger = LogManager.getLogger(ApnsService.class);
    private TextField textFieldApnsTopic;
    private TextField textFieldApnsToken;
    private TextField textFieldApnsCertFile;
    private TextField textFieldApnsCertPassword;
    private TextArea textAreaPayload;

    private PushClient pushClient;
    private Session _session;
    private boolean propsChanged;

    public ApnsService(TextField textFieldApnsTopic,
            TextField textFieldApnsToken,
            TextField textFieldApnsCertFile,
            TextField textFieldApnsCertPassword,
            TextArea textAreaPayload) {
        this.textFieldApnsTopic = textFieldApnsTopic;
        this.textFieldApnsToken = textFieldApnsToken;
        this.textFieldApnsCertFile = textFieldApnsCertFile;
        this.textFieldApnsCertPassword = textFieldApnsCertPassword;
        this.textAreaPayload = textAreaPayload;
    }

    public void init() {
        try {
            Path path = FileSystems.getDefault().getPath("config", "apns.properties");
            if (Files.exists(path)) {
                Properties prop = new Properties();
                prop.load(new FileInputStream("./config/apns.properties"));
                textFieldApnsTopic.setText(prop.getProperty("apnsTopic"));
                textFieldApnsToken.setText(prop.getProperty("token"));
                textFieldApnsCertFile.setText(prop.getProperty("apnsCertFile"));
                textFieldApnsCertPassword.setText(prop.getProperty("apnsCertPassword"));
                String payloadEncoded = prop.getProperty("apnsPayload");
                String payload = new String(Base64.getDecoder().decode(payloadEncoded));
                textAreaPayload.setText(payload);
            }
        } catch (IOException ex) {
            logger.warn("加载配置失败", ex);
        }
    }

    public void push() throws Exception {
        if (pushClient != null) {
            pushClient.close(getSession());
        }
        String pwd = textFieldApnsCertPassword.getText();
        String apnsTopic = textFieldApnsTopic.getText();
        String file = textFieldApnsCertFile.getText();
        final InputStream keyIn = new FileInputStream(file);
        final char[] pwdChars = pwd.toCharArray();
        final KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(keyIn, pwdChars);
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
        keyManagerFactory.init(keyStore, pwdChars);
        this.pushClient = new PushClient("test", apnsTopic, PushClient.APNS_HOST, keyManagerFactory);
        pushClient.connect(new IConnectionStatusListener() {
            @Override
            public void onSucceed() {
                logger.debug("onSucceed");
            }
            @Override
            public void onFailed() {
                logger.debug("onFailed");
            }
            @Override
            public void reconnect() {
                Platform.runLater(() -> ErrorDialog.show("连接到APNS服务器失败"));
            }
            @Override
            public void connected(Object session) {
                logger.debug("connected: {}", session);
                setSession((Session)session);
                Platform.runLater(() -> doPush((Session)session));
            }
        });
    }
    private void doPush(Session session) {
        String payload = textAreaPayload.getText();
        String token = textFieldApnsToken.getText();
        long expairTime = System.currentTimeMillis() + 3600_000 * 2;
        PushEvent pushEvent = new PushEvent(UUID.randomUUID().toString(), "test", token, payload, "info", expairTime);
        pushClient.push(session, pushEvent,
            new IConnectionStatusListener() {
                public void onSucceed() { logger.debug("onSucceed"); }
                public void onFailed() { logger.debug("onFailed"); }
                public void reconnect() { logger.debug("reconnect"); }
                public void connected(Object session) { logger.debug("connected: {}", session); }
            },
            new IPushStatusListener() {
                @Override
                public void onPushSucceed(PushEvent event, int succeedCount) {
                    logger.debug("推送成功");
                    Platform.runLater(() -> pushSucceed());
                }

                @Override
                public void onPushFailed(PushEvent event, int failedCount) {
                    Platform.runLater(() -> ErrorDialog.show("推送失败"));
                }

                @Override
                public void handleInvalidToken(String token) {
                }
            });
    }

    private void pushSucceed() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("通知");
        alert.setHeaderText("推送成功");
        alert.showAndWait();
        saveProps();
    }

    private synchronized void setSession(Session s) {
        this._session = s;
    }

    private synchronized Session getSession() {
        return this._session;
    }

    private void close() {
        this.pushClient.close(getSession());
        this.pushClient = null;
    }

    private void saveProps() {
        try {
            Path path = FileSystems.getDefault().getPath("config");
            if (!Files.exists(path)) {
                Files.createDirectory(path);
            }
            Properties prop = new Properties();
            prop.setProperty("apnsTopic", textFieldApnsTopic.getText());
            prop.setProperty("token", textFieldApnsToken.getText());
            prop.setProperty("apnsCertFile", textFieldApnsCertFile.getText());
            prop.setProperty("apnsCertPassword", textFieldApnsCertPassword.getText());
            prop.setProperty("apnsPayload", Base64.getEncoder().encodeToString(textAreaPayload.getText().getBytes()));
            FileOutputStream out = new FileOutputStream("./config/apns.properties");
            prop.store(out, "Apns push parameters");
            this.propsChanged = false;
        } catch (IOException ex) {
            logger.warn("保存配置失败", ex);
        }
    }

    public void propsChanged() {
        this.propsChanged = true;
    }
}
