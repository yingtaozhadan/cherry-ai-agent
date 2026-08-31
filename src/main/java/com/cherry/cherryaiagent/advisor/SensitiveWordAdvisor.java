package com.cherry.cherryaiagent.advisor;

import com.github.houbb.sensitive.word.api.IWordDeny;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.github.houbb.sensitive.word.support.deny.WordDenys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 违禁词校验 Advisor（基于 houbb/sensitive-word）
 * <p>
 * before()：校验用户输入，命中违禁词时按模式处理（REJECT 直接拦截 / MASK 脱敏替换）
 * after()：校验模型输出，命中违禁词时脱敏替换
 */
@Slf4j
public class SensitiveWordAdvisor implements BaseAdvisor {

    public static final String CONTEXT_KEY_REQUEST_WORDS = "sensitive_words_in_request";

    public enum Mode {
        /** 命中即抛异常，阻断本次调用 */
        REJECT,
        /** 命中则将违禁词替换为 * 后继续调用 */
        MASK
    }

    private final SensitiveWordBs sensitiveWordBs;
    private final Mode mode;
    private final boolean checkResponse;
    private final int order;

    public SensitiveWordAdvisor() {
        this(Mode.REJECT, true, 0);
    }

    public SensitiveWordAdvisor(Mode mode, boolean checkResponse, int order) {
        this.mode = mode;
        this.checkResponse = checkResponse;
        this.order = order;
        // 内置词库 + classpath 下的自定义词库
        this.sensitiveWordBs = SensitiveWordBs.newInstance()
                .wordDeny(WordDenys.chains(WordDenys.defaults(), new ClasspathWordDeny("sensitive-words.txt")))
                .init();
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        String userText = chatClientRequest.prompt().getUserMessage().getText();
        if (!StringUtils.hasText(userText)) {
            return chatClientRequest;
        }

        List<String> bannedWords = sensitiveWordBs.findAll(userText);
        if (bannedWords.isEmpty()) {
            return chatClientRequest;
        }

        if (mode == Mode.REJECT) {
            log.warn("用户输入命中违禁词: {}", bannedWords);
            throw new IllegalArgumentException("输入内容包含违禁词，请修改后重试");
        }

        log.warn("用户输入命中违禁词，已脱敏: {}", bannedWords);
        String maskedText = sensitiveWordBs.replace(userText);
        return chatClientRequest.mutate()
                .prompt(chatClientRequest.prompt().augmentUserMessage(maskedText))
                .context(CONTEXT_KEY_REQUEST_WORDS, bannedWords)
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        if (!checkResponse) {
            return chatClientResponse;
        }
        ChatResponse chatResponse = chatClientResponse.chatResponse();
        if (chatResponse == null || chatResponse.getResult() == null) {
            return chatClientResponse;
        }
        AssistantMessage output = chatResponse.getResult().getOutput();
        if (output == null || !StringUtils.hasText(output.getText())) {
            return chatClientResponse;
        }
        if (!sensitiveWordBs.contains(output.getText())) {
            return chatClientResponse;
        }

        String maskedText = sensitiveWordBs.replace(output.getText());
        log.warn("模型输出命中违禁词，已脱敏");
        AssistantMessage maskedOutput = AssistantMessage.builder()
                .content(maskedText)
                .properties(output.getMetadata())
                .toolCalls(output.getToolCalls())
                .media(output.getMedia())
                .build();
        List<Generation> generations = new ArrayList<>(chatResponse.getResults());
        generations.set(0, new Generation(maskedOutput, chatResponse.getResult().getMetadata()));
        ChatResponse maskedResponse = new ChatResponse(generations, chatResponse.getMetadata());
        return chatClientResponse.mutate().chatResponse(maskedResponse).build();
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    /**
     * 从 classpath 加载自定义违禁词文件（每行一个词，# 开头为注释）
     */
    static class ClasspathWordDeny implements IWordDeny {

        private final String path;

        ClasspathWordDeny(String path) {
            this.path = path;
        }

        @Override
        public List<String> deny() {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
                if (in == null) {
                    return List.of();
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    return reader.lines()
                            .map(String::trim)
                            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                            .toList();
                }
            } catch (IOException e) {
                throw new IllegalStateException("加载违禁词词库失败: " + path, e);
            }
        }
    }
}
