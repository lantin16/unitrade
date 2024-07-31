package com.lantin.unitrade.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "unitrade.cart")  // 程序运行时会自动在配置文件中搜索对应前缀的配置项，然后注入到这个类的对应属性中
public class CartProperties {
    private Integer maxItems;
}
