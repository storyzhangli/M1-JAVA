package com.zl.d05.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/*启动项目后访问：
- 文档主页：`http://localhost:8080/doc.html`
- OpenAPI 原始 JSON：`http://localhost:8080/v3/api-docs`*/
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI智能知识助手 API文档")
                        .version("1.0.0")
                        .description("Java AI项目 - 阶段1工程骨架接口文档")
                        .contact(new Contact()
                                .name("开发团队")
                                .email("dev@aijava.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("default")
                // 显式指定你的 controller 包路径 todo 路径不对，无数据
                .packagesToScan("com.zl.d05.controller")
                .pathsToMatch("/**")
                .build();
    }
}
