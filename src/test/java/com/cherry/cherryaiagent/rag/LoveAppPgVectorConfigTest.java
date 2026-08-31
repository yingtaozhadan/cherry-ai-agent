package com.cherry.cherryaiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LoveAppPgVectorConfigTest {

    @Resource
    private VectorStore pgVectorStore;

    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

    @Test
    void pgVectorStore() {
        pgVectorStore.add(loveAppDocumentLoader.loadMarkdowns());
    }
}