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


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

public class MyHttpHandler implements ProxyRequestHandler {

    private final Logging logging;   // 日志记录器

    private String whuUrl="https://gym.whu.edu.cn/api/GSOrder/Create";

    private String passtime = "18:00:00";

    private OrderInfo aOrderInfo;  //  存储 Order 对象


    private final ObjectMapper objectMapper = new ObjectMapper();    //包装器


    public MyHttpHandler(MontoyaApi api) {
        this.logging = api.logging();
        logging.logToOutput("Plugin register successs 👌");
        logging.logToOutput("Plugin version: 1.0.1");

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
            if (root.has("orderinfo")) {
                this.aOrderInfo = objectMapper.readValue(
                        root.get("orderinfo").toString(),
                        new TypeReference<OrderInfo>() {}
                );
            }

            logging.logToOutput("config load success ✅");
            logging.logToOutput("passtime = " + passtime);
            logging.logToOutput("order = " + aOrderInfo);

        } catch (IOException e) {
            logging.logToError("config load failure: " + e.getMessage());
        }
    }
    @Override
    public ProxyRequestReceivedAction handleRequestReceived(InterceptedRequest interceptedRequest) {
        String requestUrl = interceptedRequest.url();

        if (whuUrl.equals(requestUrl)) {
            HttpRequest modifiedRequest = modifyRequest(interceptedRequest,aOrderInfo);
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



    //修改拦截请求体
    private HttpRequest modifyRequest(InterceptedRequest interceptedRequest,OrderInfo orderInfo){
        String jsonPreRequest = interceptedRequest.bodyToString();
        //修改wdtoken部分===============================================
        // 目标时间：今天的 18:00:01
        LocalDateTime target = LocalDateTime.now()
                .withHour(18)
                .withMinute(0)
                .withSecond(1)
                .withNano(0);

        // 当前时间
        LocalDateTime now = LocalDateTime.now();

        // 如果当前时间还没到 18:00:01，则等待
        if (now.isBefore(target)) {
            long millisToWait = Duration.between(now, target).toMillis();
            try {
                Thread.sleep(millisToWait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        String WDVerifyToken = null;
        JsonNode responsejson = null;
        try{
            // 获取 WDVerifyToken
            String urlString = String.format(
                    "https://gym.whu.edu.cn/api/GSStadiums/GetAppointmentDetail?Version=%s&StadiumsAreaId=%s&StadiumsAreaNo=%s&AppointmentDate=%s",
                    3, this.aOrderInfo.getStadiumsAreaId(), this.aOrderInfo.getStadiumsAreaNo(), this.aOrderInfo.getAppointmentStartDate().split(" ")[0]
            );
            logging.logToOutput("\n URL :" + urlString);
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            // 设置请求方法
            conn.setRequestMethod("GET");

            // 设置请求头
            conn.setRequestProperty("accept", "*/*");
            conn.setRequestProperty("accept-language", "zh-CN,zh;q=0.9");
            conn.setRequestProperty("Authorization",interceptedRequest.headerValue("Authorization") );
            conn.setRequestProperty("content-type", "application/json");
            conn.setRequestProperty("priority", "u=1, i");
            conn.setRequestProperty("referer", "https://gym.whu.edu.cn/hsdsqhafive/pages/index/detail?areaId=11&areaNo=8&date=2025-10-29");
            conn.setRequestProperty("sec-ch-ua", "\"Google Chrome\";v=\"141\", \"Not?A_Brand\";v=\"8\", \"Chromium\";v=\"141\"");
            conn.setRequestProperty("sec-ch-ua-mobile", "?0");
            conn.setRequestProperty("sec-ch-ua-platform", "\"Windows\"");
            conn.setRequestProperty("sec-fetch-dest", "empty");
            conn.setRequestProperty("sec-fetch-mode", "cors");
            conn.setRequestProperty("sec-fetch-site", "same-origin");
            conn.setRequestProperty("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36");
            conn.setRequestProperty("Cookie",interceptedRequest.headerValue("Cookie"));
            // 发请求
            int responseCode = conn.getResponseCode();
            logging.logToOutput("Response Code: " + responseCode);

            // 读取响应
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8")
            );

            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            reader.close();
            String json = response.toString();
            logging.logToError("\n"+json+"\n");
            responsejson = new ObjectMapper().readTree(json);
            WDVerifyToken = responsejson.get("WDToken").asText();
            logging.logToOutput("Success get detail, include token "+WDVerifyToken+"\n");
//            Thread.sleep(2000);     //傻逼学校，wdtoken和create请求必须间隔2s以上
//            logging.logToOutput("SLEEP 2s\n");
        }catch (Exception e){
            logging.logToOutput("Try to get wdtoken failed!!!!!!!!!!!!!!!!!!!!!!!\n");
        }
        //@TODO: 有关智能订场，如果预期时间已经被订了，就选一个其他时间
        try {
            // 解析成树
            JsonNode requestjson = objectMapper.readTree(jsonPreRequest);

            // 转成 ObjectNode 才能修改
            if (requestjson.isObject()) {
                ObjectNode obj = (ObjectNode) requestjson;

                obj.put("appointmentStartDate", orderInfo.getAppointmentStartDate());
                obj.put("appointmentEndDate", orderInfo.getAppointmentEndDate());
                obj.put("stadiumsAreaId", orderInfo.getStadiumsAreaId());
                obj.put("stadiumsAreaNo", orderInfo.getStadiumsAreaNo());
                obj.put("WDVerifyToken", WDVerifyToken);
                String requestModifiedStr = objectMapper.writeValueAsString(obj);
                return interceptedRequest.withBody(requestModifiedStr);
            }
        } catch (Exception e) {
            logging.logToError("修改 JSON 出错: " + e.getMessage());
        }
        return interceptedRequest;
    }


}
