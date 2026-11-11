package com.hc24.springaidemo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class TripTool {

    @Tool(description = "查询城市的酒店推荐。目前只有成都的模拟数据，参数city是城市中文名")
    public String getHotels(String city) {
        if (!isChengdu(city)) {
            return "暂无" + city + "的酒店数据，目前只能查询成都";
        }
        return "成都酒店：春熙路亚朵酒店，480元/晚，距春熙路200米；宽窄巷子竹里酒店，620元/晚，紧邻宽窄巷子；成都香格里拉大酒店，980元/晚，靠近天府广场";
    }

    @Tool(description = "查询城市的特色小吃。目前只有成都的模拟数据，参数city是城市中文名")
    public String getSnacks(String city) {
        if (!isChengdu(city)) {
            return "暂无" + city + "的小吃数据，目前只能查询成都";
        }
        return "成都小吃：龙抄手的红油抄手，钟水饺的钟水饺和甜水面，冒椒火辣的冒菜，贺记蛋烘糕的蛋烘糕";
    }

    @Tool(description = "查询城市的旅游景点推荐。目前只有成都的模拟数据，参数city是城市中文名")
    public String getAttractions(String city) {
        if (!isChengdu(city)) {
            return "暂无" + city + "的景点数据，目前只能查询成都";
        }
        return "成都景点：宽窄巷子，适合逛老街；武侯祠，了解三国历史；大熊猫繁育研究基地，看大熊猫；锦里，晚上适合吃小吃";
    }

    private static boolean isChengdu(String city) {
        return city != null && city.contains("成都");
    }

}
