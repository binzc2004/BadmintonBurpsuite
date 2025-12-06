import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class Main {
    public static void main(String[] args) {
        try {
            String urlString = "https://gym.whu.edu.cn/api/GSStadiums/GetAppointmentDetail"
                    + "?Version=3&StadiumsAreaId=11&StadiumsAreaNo=8&AppointmentDate=2025-12-05";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // 设置请求方法
            conn.setRequestMethod("GET");

            // 设置请求头
            conn.setRequestProperty("accept", "*/*");
            conn.setRequestProperty("accept-language", "zh-CN,zh;q=0.9");
            conn.setRequestProperty("authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJodHRwOi8vc2NoZW1hcy54bWxzb2FwLm9yZy93cy8yMDA1LzA1L2lkZW50aXR5L2NsYWltcy9uYW1lIjoiMjAyMjMwMjExMTE1MCIsImp0aSI6IjIxMjUiLCJodHRwOi8vc2NoZW1hcy5taWNyb3NvZnQuY29tL3dzLzIwMDgvMDYvaWRlbnRpdHkvY2xhaW1zL2V4cGlyYXRpb24iOiIxMi83LzIwMjUgNTo1NjowMSBBTSIsImh0dHA6Ly9zY2hlbWFzLnhtbHNvYXAub3JnL3dzLzIwMDUvMDUvaWRlbnRpdHkvY2xhaW1zL3N5c3RlbSI6Img1IiwiaHR0cDovL3NjaGVtYXMubWljcm9zb2Z0LmNvbS93cy8yMDA4LzA2L2lkZW50aXR5L2NsYWltcy9yb2xlIjoiQ2xpZW50IiwibmJmIjoxNzY1MDE0OTYxLCJleHAiOjE3NjUwNTgxNjEsImlzcyI6Ikd5bVJlc2VydmF0aW9uIiwiYXVkIjoid3IifQ.V35JFFxpkkt6LFi0qf3iwGULFrvqVF0mEwy5qK-1X-Q");
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
            conn.setRequestProperty("Cookie", "_dx_captcha_cid=30037963; _dx_uzZo5y=fbd4f7910bb06e38d1550dcae3b5a5bb51747ea3b7c944dec71c82e4def4bdc04cbfab7a; _dx_FMrPY6=69315afblZCBy2b2KVlmcWIlG5P8tJRH9COEEoh1; _dx_app_635a429a5f66919cef86083594bdd722=69315afblZCBy2b2KVlmcWIlG5P8tJRH9COEEoh1; SF_cookie_1=22201494; _dx_captcha_vid=02EB3C677996211DF49E64E8A8F1076D3EF49934CA09FAE4902C278F6538009CA8DF84D4E30B7542FCE34CDF5163C054F144A7D4036C0DD80D7777D0F9ADEB2B2AF0E8C68BDEF95B0E40D20F0B57F51F");

            // 发送请求
            int responseCode = conn.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            // 读取响应内容
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            String json = response.toString();
            JsonNode root = new ObjectMapper().readTree(json);
             String WDVerifyToken = root.get("WDToken").asText();
            root.get("response").get("AppointmentTimes");
             System.out.println(WDVerifyToken);
             System.out.println();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
