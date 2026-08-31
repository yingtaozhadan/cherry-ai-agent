package com.cherry.cherryaiagent.rag;

import cn.hutool.core.lang.UUID;
import com.cherry.cherryaiagent.app.LoveApp;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LoveAppDocumentLoaderTest {

    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

    @Resource
    private LoveApp loveApp;

    @Test
    void loadMarkdowns() {
        loveAppDocumentLoader.loadMarkdowns();
    }

    @Test
    void doChat() {
    }

    @Test
    void doChatWithReport() {
    }

    @Test
    void doChatWithRag() {
        loveApp.doChatWithRag("怎样在社交场合主动结识心仪异性", UUID.randomUUID().toString());
    }
}