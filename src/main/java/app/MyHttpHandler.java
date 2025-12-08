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

    private int sleeptime = 2000;

    private java.net.http.HttpClient client =
            java.net.http.HttpClient.newBuilder()
                    .version(java.net.http.HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();

    private OrderInfo aOrderInfo;  //  存储 Order 对象


    private final ObjectMapper objectMapper = new ObjectMapper();    //包装器


    public MyHttpHandler(MontoyaApi api) {
        this.logging = api.logging();
        logging.logToOutput("Plugin register successs 👌");
        logging.logToOutput("Plugin version: 3.0.1");

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
            if (root.has("sleeptime")) {
                this.sleeptime = root.get("sleeptime").asInt();
            }

            // 取 orderinfos 并映射成 List<OrderInfo>
            if (root.has("orderinfo")) {
                this.aOrderInfo = objectMapper.readValue(
                        root.get("orderinfo").toString(),
                        new TypeReference<OrderInfo>() {}
                );
            }

            logging.logToOutput("config load success ✅");
            logging.logToOutput("sleeptime = " + sleeptime);
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
            PostCreate(interceptedRequest);
            return null;
        }else{
            return ProxyRequestToBeSentAction.continueWith(interceptedRequest);
        }
    }

    public void sleepUntilRelease() {
        try {
            Thread.sleep(this.sleeptime);
            logging.logToOutput("SLEEP " + this.sleeptime + "ms");
        } catch (InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt(); // 重新设置中断状态
        }
    }

    /**
     * 直接发出请求，不是释放请求，或许这样会快一点？
     */
    private void PostCreate(InterceptedRequest interceptedRequest) {

        try {
            // ===== 构造 URL =====
            String url = interceptedRequest.url();
            logging.logToOutput("POST URL: " + url);

            // ===== 请求体 =====
            String body = interceptedRequest.bodyToString();
            logging.logToOutput("Request body Modified: " + body + "\n");

            // ===== 构造 Builder =====
            java.net.http.HttpRequest.Builder builder =
                    java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(url))
                            .timeout(Duration.ofSeconds(5))
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body));

            // ===== 复制 interceptedRequest headers =====
            interceptedRequest.headers().forEach(h -> {
                try {
                    builder.header(h.name(), h.value());
                } catch (Exception ignored) {}
            });

            // ===== 构造请求 =====
            java.net.http.HttpRequest httpRequest = builder.build();

            // ===== 发送请求 =====
            // 输出当前时间
            logging.logToOutput(
                    "Post request: " +
                            java.time.LocalDateTime.now()
                                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
            );
            java.net.http.HttpResponse<String> response =
                    client.send(httpRequest, java.net.http.HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            String respBody = response.body();

            logging.logToOutput("POST Response Code: " + code);
            // 输出当前时间
            logging.logToOutput(
                    "Receive request: " +
                            java.time.LocalDateTime.now()
                                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
            );
            logging.logToError("POST Response Body: " + respBody + "\n");

        } catch (Exception e) {
            logging.logToError("PostCreate Error: " + e.getMessage());
        }
    }




    //修改拦截请求体
    private HttpRequest modifyRequest(InterceptedRequest interceptedRequest,OrderInfo orderInfo){
        String jsonPreRequest = interceptedRequest.bodyToString();
        //修改wdtoken部分===============================================
        // 目标时间：今天的 18:00:01
        int millions = 300;
        LocalDateTime target = LocalDateTime.now()
                .withHour(18)
                .withMinute(0)
                .withSecond(0)

                .withNano(1000000*millions);

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
        logging.logToOutput(
                "Start to get WDToken: " +
                        java.time.LocalDateTime.now()
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
        );

        String WDVerifyToken = null;
        JsonNode responsejson = null;

        try {
            // 构造 URL
            String urlString = String.format(
                    "https://gym.whu.edu.cn/api/GSStadiums/GetAppointmentDetail?Version=%s&StadiumsAreaId=%s&StadiumsAreaNo=%s&AppointmentDate=%s",
                    3,
                    this.aOrderInfo.getStadiumsAreaId(),
                    this.aOrderInfo.getStadiumsAreaNo(),
                    this.aOrderInfo.getAppointmentStartDate().split(" ")[0]
            );

            logging.logToOutput("\n URL :" + urlString);

            // 构建请求（也使用全限定名）
            java.net.http.HttpRequest request =
                    java.net.http.HttpRequest.newBuilder()
                            .uri(URI.create(urlString))
                            .timeout(Duration.ofSeconds(5))
                            .header("accept", "*/*")
                            .header("accept-language", "zh-CN,zh;q=0.9")
                            .header("Authorization", interceptedRequest.headerValue("Authorization"))
                            .header("content-type", "application/json")
                            .header("priority", "u=1, i")
                            .header("referer", "https://gym.whu.edu.cn/hsdsqhafive/pages/index/detail?areaId=11&areaNo=8&date=2025-10-29")
                            .header("sec-ch-ua", "\"Google Chrome\";v=\"141\", \"Not?A_Brand\";v=\"8\", \"Chromium\";v=\"141\"")
                            .header("sec-ch-ua-mobile", "?0")
                            .header("sec-ch-ua-platform", "\"Windows\"")
                            .header("sec-fetch-dest", "empty")
                            .header("sec-fetch-mode", "cors")
                            .header("sec-fetch-site", "same-origin")
                            .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                            .header("Cookie", interceptedRequest.headerValue("Cookie"))
                            .GET()
                            .build();

            // 发起请求（使用全限定名的 BodyHandlers）
            java.net.http.HttpResponse<String> response =
                  this.client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            logging.logToOutput("Response Code: " + response.statusCode());

            String json = response.body();

            logging.logToError("\n" + json + "\n");

            // 解析 JSON
            responsejson = new ObjectMapper().readTree(json);

            WDVerifyToken = responsejson.get("WDToken").asText();
            logging.logToOutput("Success get detail, include token " + WDVerifyToken + "\n");
            logging.logToOutput("Token time: " + responsejson.get("WDTokenTime").asText());

        } catch (Exception e) {
            logging.logToOutput("Try to get wdtoken failed!!!!!!!!!!!!!!!!!!!!!!!\n");
        }
        //@TODO: 有关智能订场，如果预期时间已经被订了，就选一个其他时间
        try{
            SmartOrder(aOrderInfo,responsejson.get("response").get("AppointmentTimes"));
        }catch (Exception e){
            logging.logToOutput("SmartOrder failed!!!!!!!!!!!!!!!!!!!!!!!\n");
        }
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
    private void SmartOrder(OrderInfo aOrderInfo, JsonNode arrNodes) {

        // ========== 1. 计算需要连续几个小时 ==========
        String startStr = aOrderInfo.getAppointmentStartDate().substring(11); // "19:00"
        String endStr   = aOrderInfo.getAppointmentEndDate().substring(11);   // "21:00"

        int needHours = Integer.parseInt(endStr.substring(0, 2)) -
                Integer.parseInt(startStr.substring(0, 2));

        // ========== 2. 找到预期起点对应的 index ==========
        int wantIndex = -1;
        for (int i = 0; i < arrNodes.size(); i++) {
            if (arrNodes.get(i).get("StartTime").asText().equals(startStr)) {
                wantIndex = i;
                break;
            }
        }

        if (wantIndex == -1) {
            logging.logToOutput("Expected start time not found");
            return;
        }

        // ========== 3. 先检查预期时间段是否可用 ==========
        if (isOk(arrNodes, wantIndex, needHours)) {
            logging.logToOutput("Expected time is available");
            return;
        }

        // ========== 4. 不可用 → 滑动窗口，从后往前找 ==========
        int lastStartIndex = arrNodes.size() - needHours;

        for (int i = lastStartIndex; i >= 0; i--) {
            if (isOk(arrNodes, i, needHours)) {

                JsonNode s = arrNodes.get(i);
                JsonNode e = arrNodes.get(i + needHours - 1);

                String date = aOrderInfo.getAppointmentStartDate().substring(0, 10);

                String newStart = date + " " + s.get("StartTime").asText();
                String newEnd   = date + " " + e.get("EndTime").asText();

                aOrderInfo.setAppointmentStartDate(newStart);
                aOrderInfo.setAppointmentEndDate(newEnd);

                logging.logToError("Expected time is unavilabe,now adjust to: " + newStart + " ~ " + newEnd);
                return;
            }
        }

        // ========== 5. 全都找不到 ==========
        logging.logToError("!!!!!! Today don't have any time ");
    }

    private boolean isOk(JsonNode arrNodes, int start, int needHours) {
        if (start + needHours > arrNodes.size()) return false;
        for (int i = 0; i < needHours; i++) {
            if (arrNodes.get(start + i).get("IsCanAppointment").asInt() != 1) {
                return false;
            }
        }
        return true;
    }
}
