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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MyHttpHandler implements ProxyRequestHandler {

    private final Logging logging;   // 日志记录器

    private String whuUrl="https://gym.whu.edu.cn/api/GSOrder/Create";

    private int timedelay = 0;       // 默认是18:00就放行

    private List<OrderInfo> orderInfos;  // 用 List 存储 Order 对象
    private final AtomicInteger index = new AtomicInteger(-1); //下标

    private final ObjectMapper objectMapper = new ObjectMapper();    //包装器


    public MyHttpHandler(MontoyaApi api) {
        this.logging = api.logging();
        logging.logToOutput("插件注册成功 👌");

        // 获取用户目录
        String userHome = System.getProperty("user.home");
        File configFile = new File(userHome, "BadmintonConfig.json");

        if (!configFile.exists()) {
            logging.logToError("配置文件不存在: " + configFile.getAbsolutePath());
            return;
        }

        try {
            // 先读成树形结构
            JsonNode root = objectMapper.readTree(configFile);

            // 取 timedelay
            if (root.has("timedelay")) {
                this.timedelay = root.get("timedelay").asInt();
            }

            // 取 orderinfos 并映射成 List<OrderInfo>
            if (root.has("orderinfos")) {
                this.orderInfos = objectMapper.readValue(
                        root.get("orderinfos").toString(),
                        new TypeReference<List<OrderInfo>>() {}
                );
            }

            logging.logToOutput("配置加载成功 ✅");
            logging.logToOutput("timedelay = " + timedelay);
            logging.logToOutput("orders = " + orderInfos);

        } catch (IOException e) {
            logging.logToError("加载配置失败: " + e.getMessage());
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
            logging.logToOutput("Request body: " + interceptedRequest.bodyToString()+"\n\n");

        }
        return ProxyRequestToBeSentAction.continueWith(interceptedRequest);
    }

    public void sleepUntilRelease() {
        LocalDateTime now = LocalDateTime.now();
        // 基础放行时间：今天 18:00:00
        LocalDateTime targetTime = now.with(LocalTime.of(18, 0, 0));

        // 如果已经过了18:00，目标时间就设置为明天的18:00
        if (now.isAfter(targetTime)) {
            targetTime = targetTime.plusDays(1);
        }

        // 加上 timedelay（单位：秒）
        targetTime = targetTime.plusSeconds(timedelay);

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
