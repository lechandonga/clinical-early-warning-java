package com.github.lechandonga.clinicalearlywarningjava.tests;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Vincent
 * @version 1.0
 */
@SpringBootTest
public class ApplicationTests {
    private static final Logger log = LoggerFactory.getLogger(ApplicationTests.class);

    @Test
    public void contentLoad() {
        log.info("[启动冒烟] Spring 上下文加载成功：规则种子、内存存储、REST、模拟器 Bean 均已装配");
    }
}
