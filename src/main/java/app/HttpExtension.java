package app;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

public class HttpExtension implements BurpExtension {
    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("武汉大学羽毛球场预约BurpSuite插件");
        api.proxy().registerRequestHandler(new MyHttpHandler(api));
    }
}
