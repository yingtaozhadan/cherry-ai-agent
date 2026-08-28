package com.cherry.cherryaiagent.demo.invoke;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ChatModelInvokeTest {

    @Resource
    private ChatModelInvoke chatModelInvoke;

    @Test
    void doChatWithOpenAi() {
        chatModelInvoke.doChatWithOpenAi();
    }
}