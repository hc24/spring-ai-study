package com.hc24.springaidemo.port;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 外部 REST 适配层的本地验证端点（脚手架，非生产代码）。
 *
 * <p>默认由 app.external.base-url 指向它，因此断网也能跑通「LLM 调用外部 REST 工具」闭环。
 * 生产环境把 app.external-mock.enabled 置为 false 并改 base-url 即可关闭。</p>
 */
@RestController
@ConditionalOnProperty(name = "app.external-mock.enabled", havingValue = "true", matchIfMissing = true)
public class MockExternalApiController {

    @GetMapping("/mock/city")
    public String cityInfo(@RequestParam(name = "name", defaultValue = "") String name) {
        return "外部系统返回：" + name + " 今日出行指数 4 星，适宜出游（mock 数据，仅用于验证外部工具链路）";
    }

}
