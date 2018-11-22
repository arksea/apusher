package net.arksea.pusher.tools;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class TestToolMainController {
    @FXML
    private TextField textFieldApnsTopic;
    @FXML
    private TextField textFieldApnsToken;
    @FXML
    private TextField textFieldApnsCertFile;
    @FXML
    private TextField textFieldApnsCertPassword;
    @FXML
    private TextArea textAreaApnsPayload;
    @FXML
    private TextField textFieldHuaweiAppId;
    @FXML
    private TextField textFieldHuaweiAppKey;
    @FXML
    private TextField textFieldHuaweiToken;
    @FXML
    private TextArea textAreaHuaweiPayload;

    private ApnsService apns;
    private HuaweiService huawei;

    @FXML
    protected void initialize() {
        apns = new ApnsService(textFieldApnsTopic,
            textFieldApnsToken,
            textFieldApnsCertFile,
            textFieldApnsCertPassword,
            textAreaApnsPayload);
        apns.init();
        huawei = new HuaweiService(textFieldHuaweiAppId,
            textFieldHuaweiAppKey,
            textFieldHuaweiToken,
            textAreaHuaweiPayload);
        huawei.init();
    }

    public void onBtnApnsPushClick(ActionEvent event) throws Exception {
        try {
            apns.push();
        } catch (Exception ex) {
            ErrorDialog.show("连接APNS服务器失败", ex);
        }
    }

    public void onApnsPropsChanged() {
        apns.propsChanged();
    }

    public void onBtnHuaweiPushClick(ActionEvent event) throws Exception {
        try {
            huawei.push();
        } catch (Exception ex) {
            ErrorDialog.show("连接华为服务器失败", ex);
        }
    }

    public void onHuaweiPropsChanged() {
        huawei.propsChanged();
    }
}
