package com.hc24.springaidemo.port;

import com.hc24.springaidemo.tool.WeatherTool;



import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiController {
    private final ChatClient chatClient;

    @Resource
    private WeatherTool weatherTool;

    public AiController(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @GetMapping("/chat")
    public String chat(String msg){
        return chatClient.prompt(msg).call().content();
    }

    // 框架自动工具调用的并列演示路径，仍只挂 1 个内部工具；
    // 含外部工具的统一工具集见 /react 与 /react/stream（由 ToolCatalog 提供）
    @GetMapping("/toolAgent")
    public String agentDemo(String question){
        return chatClient.prompt(question)
                .tools(weatherTool)
                .call()
                .content();
    }
}
