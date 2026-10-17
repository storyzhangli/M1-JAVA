package com.zl.d04.config;

import com.github.xiaoymin.knife4j.core.conf.GlobalConstants;
import com.github.xiaoymin.knife4j.spring.configuration.Knife4jProperties;
import com.github.xiaoymin.knife4j.spring.configuration.Knife4jSetting;
import com.github.xiaoymin.knife4j.spring.extension.OpenApiExtensionResolver;
import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.Map;

/**
 * 原理说明：
 * Knife4j 4.5.0 的自定义器针对旧版 Springdoc 编译。
 * Springdoc 2.8.x 的相关方法签名发生变化。
 *
 * 这里覆盖 customise 方法，不调用父类原有实现，
 * 避免触发不兼容的 addOrderExtension 逻辑。
 */
@Primary
@Configuration
public class Knife4jOpenApiCustomizer
        extends com.github.xiaoymin.knife4j.spring.extension.Knife4jOpenApiCustomizer
        implements GlobalOpenApiCustomizer {

    private final Knife4jProperties knife4jProperties;

    public Knife4jOpenApiCustomizer(
            Knife4jProperties knife4jProperties,
            SpringDocConfigProperties springDocConfigProperties) {
        super(knife4jProperties, springDocConfigProperties);
        this.knife4jProperties = knife4jProperties;
    }

    @Override
    public void customise(OpenAPI openApi) {
        // 关闭增强配置时不添加 Knife4j 扩展信息。
        if (!knife4jProperties.isEnable()) {
            return;
        }

        Knife4jSetting setting = knife4jProperties.getSetting();

        OpenApiExtensionResolver resolver = new OpenApiExtensionResolver(
                setting,
                knife4jProperties.getDocuments()
        );

        resolver.start();

        Map<String, Object> extensions = new HashMap<>();
        extensions.put(
                GlobalConstants.EXTENSION_OPEN_SETTING_NAME,
                setting
        );
        extensions.put(
                GlobalConstants.EXTENSION_OPEN_MARKDOWN_NAME,
                resolver.getMarkdownFiles()
        );

        openApi.addExtension(
                GlobalConstants.EXTENSION_OPEN_API_NAME,
                extensions
        );
    }
}