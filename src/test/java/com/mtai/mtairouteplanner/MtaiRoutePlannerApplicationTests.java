package com.mtai.mtairouteplanner;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "route.session.store=memory",
        "route.intent.agent=fake",
        "route.presenter.agent=fake",
        "route.events.publisher=noop",
        "route.data.source=json",
        "spring.ai.model.chat=none"
})
class MtaiRoutePlannerApplicationTests {

    @Test
    void contextLoads() {
    }

}
