package com.hc24.springaidemo.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hc24.springaidemo.tool.LocalTool;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Collects every {@link LocalTool} bean and, when enabled, MCP tools into one
 * {@link ToolCallback} array for the manual ReAct loop. A new local tool only
 * needs to implement {@link LocalTool}.
 *
 * <p>Spring AI 的 ToolCallingAutoConfiguration 会按类名刻意把
 * SyncMcpToolCallbackProvider 排除在默认 ToolCallbackResolver 之外，
 * 因此这里必须显式收集 MCP provider，不能指望框架自动聚合。</p>
 *
 * <p>工具只在启动时收集一次并缓存，避免每轮 react 都触发 MCP 的 listTools()；
 * 单个 MCP Server 不可用时只跳过它，绝不让 Spring 上下文初始化失败。</p>
 */
@Component
public class ToolCatalog {

    private final ToolCallback[] toolCallbacks;

    public ToolCatalog(ObjectProvider<LocalTool> localTools,
            ObjectProvider<ToolCallbackProvider> mcpProviders,
            @Value("${app.mcp-enabled:false}") boolean mcpEnabled) {
        // Local tools win on name conflicts; a later MCP tool with the same name is ignored.
        Map<String, ToolCallback> tools = new LinkedHashMap<>();
        List<LocalTool> localToolBeans = localTools.orderedStream().toList();
        if (!localToolBeans.isEmpty()) {
            addAll(tools, ToolCallbacks.from(localToolBeans.toArray()));
        }

        if (mcpEnabled) {
            for (ToolCallbackProvider provider : mcpProviders.orderedStream().toList()) {
                try {
                    // getToolCallbacks() 会触发 listTools()，是网络/子进程调用，必须兜底
                    addAll(tools, provider.getToolCallbacks());
                }
                catch (Exception e) {
                    System.out.println("[ToolCatalog] MCP 工具源不可用，已跳过："
                            + provider.getClass().getSimpleName() + " - " + e.getMessage());
                }
            }
        }

        this.toolCallbacks = tools.values().toArray(new ToolCallback[0]);
        System.out.println("[ToolCatalog] 已注册工具 " + this.toolCallbacks.length + " 个：");
        for (ToolCallback tool : this.toolCallbacks) {
            System.out.println("  - " + tool.getToolDefinition().name());
        }
    }

    private static void addAll(Map<String, ToolCallback> tools, ToolCallback[] callbacks) {
        for (ToolCallback callback : callbacks) {
            tools.putIfAbsent(callback.getToolDefinition().name(), callback);
        }
    }

    public ToolCallback[] getToolCallbacks() {
        return this.toolCallbacks;
    }

}
