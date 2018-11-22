package net.arksea.pusher.tools;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import net.arksea.pusher.IConnectionStatusListener;
import net.arksea.pusher.IPushStatusListener;
import net.arksea.pusher.PushEvent;
import net.arksea.pusher.PushStatus;
import net.arksea.pusher.huawei.PushClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Properties;
import java.util.UUID;

/**
 *
 * Created by xiaohaixing on 2018/11/5.
 */
public class HuaweiService {
    Logger logger = LogManager.getLogger(HuaweiService.class);
    private TextField textFieldHuaweiAppKey;
    private TextField textFieldHuaweiToken;
    private TextField textFieldHuaweiAppId;
    private TextArea textAreaPayload;

    private PushClient pushClient;
    private String session;
    private boolean propsChanged;

    public HuaweiService(TextField textFieldHuaweiAppId,
                       TextField textFieldHuaweiAppKey,
                       TextField textFieldHuaweiToken,
                       TextArea textAreaPayload) {
        this.textFieldHuaweiAppId = textFieldHuaweiAppId;
        this.textFieldHuaweiAppKey = textFieldHuaweiAppKey;
        this.textFieldHuaweiToken = textFieldHuaweiToken;
        this.textAreaPayload = textAreaPayload;
    }

    public void init() {
        try {
            Path path = Paths.get("config", "huawei.properties");
            if (Files.exists(path)) {
                Properties prop = new Properties();
                prop.load(new FileInputStream("./config/huawei.properties"));
                textFieldHuaweiAppId.setText(prop.getProperty("appId"));
                textFieldHuaweiAppKey.setText(prop.getProperty("appKey"));
                textFieldHuaweiToken.setText(prop.getProperty("token"));
                String payloadEncoded = prop.getProperty("payload");
                String payload = new String(Base64.getDecoder().decode(payloadEncoded.getBytes("UTF-8")), "UTF-8");
                textAreaPayload.setText(payload);
            }
        } catch (IOException ex) {
            logger.warn("加载配置失败", ex);
        }
    }

    public void push() throws Exception {
        if (pushClient != null) {
            pushClient.close(session);
        }
        String appId = textFieldHuaweiAppId.getText();
        String appKey = textFieldHuaweiAppKey.getText();
        this.pushClient = new PushClient(appId, appKey);
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
                ErrorDialog.show("连接到华为服务器失败");
            }
            @Override
            public void connected(Object session) {
                logger.debug("connected: {}", session);
                setSession((String)session);
                doPush((String)session);
            }
        });

    }
    private void doPush(String session) {
        String payload = textAreaPayload.getText();
        String token = textFieldHuaweiToken.getText();
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
                public void onComplete(PushEvent event, PushStatus status) {
                    Platform.runLater(() -> close(session));
                    switch (status) {
                        case PUSH_FAILD:
                            ErrorDialog.show("推送失败");
                            break;
                        case INVALID_TOKEN:
                            ErrorDialog.show("Token无效");
                            break;
                        case PUSH_SUCCEED:
                            logger.debug("推送成功");
                            pushSucceed();
                            break;
                        default:
                            break;
                    }
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

    private void setSession(String session) {
        this.session = session;
    }

    private void close(String session) {
        this.pushClient.close(session);
        this.pushClient = null;
    }

    private void saveProps() {
        try {
            Path path = Paths.get("config");
            if (!Files.exists(path)) {
                Files.createDirectory(path);
            }
            Properties prop = new Properties();
            prop.setProperty("appId", textFieldHuaweiAppId.getText());
            prop.setProperty("appKey", textFieldHuaweiAppKey.getText());
            prop.setProperty("token", textFieldHuaweiToken.getText());
            prop.setProperty("payload", Base64.getEncoder().encodeToString(textAreaPayload.getText().getBytes("UTF-8")));
            FileOutputStream out = new FileOutputStream("./config/huawei.properties");
            prop.store(out, "Huawei push parameters");
            this.propsChanged = false;
        } catch (IOException ex) {
            logger.warn("保存配置失败", ex);
        }
    }

    public void propsChanged() {
        this.propsChanged = true;
    }

}
