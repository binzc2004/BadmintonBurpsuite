package app;

import app.pojo.OrderInfo;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.proxy.http.InterceptedRequest;
import burp.api.montoya.proxy.http.ProxyRequestHandler;
import burp.api.montoya.proxy.http.ProxyRequestReceivedAction;
import burp.api.montoya.proxy.http.ProxyRequestToBeSentAction;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;


import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MyHttpHandler implements ProxyRequestHandler {

    private final Logging logging;   // 日志记录器

    private String whuUrl="https://gym.whu.edu.cn/api/GSOrder/Create";

    private String passtime = "18:00:00";

    private List<OrderInfo> orderInfos;  // 用 List 存储 Order 对象
    private final AtomicInteger index = new AtomicInteger(-1); //下标

    private final ObjectMapper objectMapper = new ObjectMapper();    //包装器


    public MyHttpHandler(MontoyaApi api) {
        this.logging = api.logging();
        logging.logToOutput("Plugin register successs 👌");

        // 获取用户目录
        String userHome = System.getProperty("user.home");
        File configFile = new File(userHome, "BadmintonConfig.json");

        if (!configFile.exists()) {
            logging.logToError("File not found: " + configFile.getAbsolutePath());
            return;
        }

        try {
            // 先读成树形结构
            JsonNode root = objectMapper.readTree(configFile);

            // 取 passtime 字段
            if (root.has("passtime")) {
                this.passtime = root.get("passtime").asText();
            }

            // 取 orderinfos 并映射成 List<OrderInfo>
            if (root.has("orderinfos")) {
                this.orderInfos = objectMapper.readValue(
                        root.get("orderinfos").toString(),
                        new TypeReference<List<OrderInfo>>() {}
                );
            }

            logging.logToOutput("config load success ✅");
            logging.logToOutput("passtime = " + passtime);
            logging.logToOutput("orders = " + orderInfos);

        } catch (IOException e) {
            logging.logToError("config load failure: " + e.getMessage());
        }
    }
    @Override
    public ProxyRequestReceivedAction handleRequestReceived(InterceptedRequest interceptedRequest) {
        String requestUrl = interceptedRequest.url();

        if (whuUrl.equals(requestUrl)) {
            int iindex = this.index.incrementAndGet();
            logging.logToOutput("Times " + iindex + " Received request: " + interceptedRequest.url());

            String jsonInput = interceptedRequest.bodyToString();
            OrderInfo torder = this.orderInfos.get(iindex);

            // 修改请求体 JSON
            String bodyModified = modifyJsonFields(jsonInput, torder);

            HttpRequest modifiedRequest = interceptedRequest.withBody(bodyModified);
            return ProxyRequestReceivedAction.continueWith(modifiedRequest);
        } else {
            return ProxyRequestReceivedAction.continueWith(interceptedRequest);
        }
    }


    @Override
    public ProxyRequestToBeSentAction handleRequestToBeSent(InterceptedRequest interceptedRequest) {
        String requestUrl=interceptedRequest.url();
        if( whuUrl.equals(requestUrl)) {
            sleepUntilRelease();
            // 输出当前时间
            logging.logToOutput("Current time: " + java.time.LocalDateTime.now());
            // 输出请求体
            logging.logToOutput("Request body Modified: " + interceptedRequest.bodyToString()+"\n\n");

        }
        return ProxyRequestToBeSentAction.continueWith(interceptedRequest);
    }

    public void sleepUntilRelease() {
        LocalDateTime now = LocalDateTime.now();

        // 解析 passtime 字符串为 LocalTime
        LocalTime passLocalTime;
        try {
            passLocalTime = LocalTime.parse(passtime); // passtime 格式必须是 "HH:mm:ss"
        } catch (DateTimeParseException e) {
            logging.logToError("Invalid passtime format: " + passtime);
            return;
        }

        LocalDateTime targetTime = now.with(passLocalTime);

        // 如果已经过了 passtime
        if (now.isAfter(targetTime)) {
            logging.logToError("Current time is already past passtime: " + passtime);
            return;
        }

        Duration duration = Duration.between(now, targetTime);
        long millisToSleep = duration.toMillis();

        logging.logToOutput("Sleeping for " + millisToSleep +
                " milliseconds until " + targetTime);

        try {
            Thread.sleep(millisToSleep);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt(); // 重新设置中断状态
        }
    }


    private String modifyJsonFields(String jsonInput, OrderInfo orderInfo) {
        try {
            // 解析成树
            JsonNode root = objectMapper.readTree(jsonInput);

            // 转成 ObjectNode 才能修改
            if (root.isObject()) {
                ObjectNode obj = (ObjectNode) root;

                obj.put("appointmentStartDate", orderInfo.getAppointmentStartDate());
                obj.put("appointmentEndDate", orderInfo.getAppointmentEndDate());
                obj.put("stadiumsAreaId", orderInfo.getStadiumsAreaId());
                obj.put("stadiumsAreaNo", orderInfo.getStadiumsAreaNo());

                return objectMapper.writeValueAsString(obj);
            }
        } catch (Exception e) {
            logging.logToError("修改 JSON 出错: " + e.getMessage());
        }
        // 出错就返回原始
        return jsonInput;
    }


}
