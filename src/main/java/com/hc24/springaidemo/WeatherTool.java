package com.hc24.springaidemo;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

// 工具类
@Component
public class WeatherTool {
    @Tool(description = "查询指定城市当前天气，参数city是城市中文名")
    public String getWeather(String city) {
        return city + "：晴天，26℃";
    }
}