package com.hc24.springaidemo.tool;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 外部 REST API 适配层：把外部 HTTP 接口包装成 LLM 可调用的 @Tool。
 *
 * <p>使用 spring-web 自带的 RestClient，无需引入新的 HTTP 依赖。
 * 地址与超时全部配置化；外部服务不可用时返回中文提示而不是抛异常，
 * 这样手写 ReAct 循环里仍能得到一条完整的 tool_result。</p>
 */
@Component
public class ExternalApiTool {

    private final RestClient restClient;

    public ExternalApiTool(
            @Value("${app.external.base-url}") String baseUrl,
            @Value("${app.external.connect-timeout:2s}") Duration connectTimeout,
            @Value("${app.external.read-timeout:3s}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Tool(description = "查询外部系统的城市出行指数与推荐信息。需要外部数据或内部工具查不到该城市时使用，参数city是城市名称")
    public String queryExternalCityInfo(String city) {
        try {
            String body = this.restClient.get()
                    .uri("/city?name={name}", city == null ? "" : city)
                    .retrieve()
                    .body(String.class);
            return body == null ? "外部系统未返回内容" : body;
        }
        catch (Exception e) {
            // 外部服务不可用不能中断整个 ReAct 循环，把失败作为工具结果交回模型
            return "外部 REST 服务暂不可用（" + e.getClass().getSimpleName() + "），无法查询：" + city;
        }
    }

}
