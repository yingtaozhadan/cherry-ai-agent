package com.cherry.cherryaiagent.app;

import com.cherry.cherryaiagent.advisor.CustomLoggerAdvisor;
import com.cherry.cherryaiagent.advisor.SensitiveWordAdvisor;
import com.cherry.cherryaiagent.chatmemory.FileBasedChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class LoveApp {

    /** 模板变量：角色名 */
    private static final String ROLE_NAME = "心悦";

    /** 模板变量：沟通风格 */
    private static final String COMMUNICATION_STYLE = """
            - 语气温暖自然，像一位可靠的朋友，避免说教和空洞套话
            - 回复简洁口语化，每次聚焦一个重点，不堆砌长篇大论
            - 多用开放式问题引导用户表达
            - 不评判用户的任何感受和选择""";

    /** 恋爱报告场景追加的系统指令 */
    private static final String REPORT_SUFFIX = "每次对话后都要生成恋爱结果，标题为{用户名}的恋爱报告，内容为建议列表";

    private final ChatClient chatClient;

    /** 渲染后的基础系统提示词（启动时渲染一次，doChatWithReport 等场景复用） */
    private final String baseSystemPrompt;

    public LoveApp(ChatModel openAiChatModel,
                   @Value("classpath:prompts/love-advisor-system.st") org.springframework.core.io.Resource systemPromptResource) {

        // 1. 渲染系统提示词模板（角色名、沟通风格等以变量方式注入）
        this.baseSystemPrompt = PromptTemplate.builder()
                .resource(systemPromptResource)
                .variables(Map.of(
                        "roleName", ROLE_NAME,
                        "communicationStyle", COMMUNICATION_STYLE))
                .build()
                .render();

        // 2. 基于文件的会话记忆
        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
        FileBasedChatMemory fileBasedChatMemory = new FileBasedChatMemory(fileDir);

        // 3. 构建 ChatClient
        this.chatClient = ChatClient.builder(openAiChatModel)
                .defaultSystem(this.baseSystemPrompt)
                .defaultAdvisors(
                        new SensitiveWordAdvisor(SensitiveWordAdvisor.Mode.MASK, false, -1),
                        MessageChatMemoryAdvisor.builder(fileBasedChatMemory).build(),
                        new CustomLoggerAdvisor()
                )
                .build();
    }

    String doChat(String userInput, String chatId) {
        ChatResponse chatResponse = chatClient.prompt()
                .advisors(advisorSpec -> advisorSpec
                        .param(ChatMemory.CONVERSATION_ID, chatId))
                .user(userInput)
                .call()
                .chatResponse();
        String text = chatResponse.getResult().getOutput().getText();
        log.info("msg is :{}", text);
        return text;
    }

    record LoveReport(String title, List<String> suggestions) {

    }

    /**
     * 结构化输出演示
     * @param userInput
     * @param chatId
     * @return
     */
    String doChatWithReport(String userInput, String chatId) {
        LoveReport loveReport = chatClient.prompt()
                .system(this.baseSystemPrompt + REPORT_SUFFIX)
                .advisors(advisorSpec -> advisorSpec
                        .param(ChatMemory.CONVERSATION_ID, chatId))
                .user(userInput)
                .call()
                .entity(LoveReport.class);
        log.info("msg is :{}", loveReport);
        return loveReport.toString();
    }

    @Resource
    private VectorStore loveAppVectorStore;

    public String doChatWithRag(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(QuestionAnswerAdvisor.builder(loveAppVectorStore).build())
                .user(message)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("msg is :{}", content);
        return content;
    }

}
