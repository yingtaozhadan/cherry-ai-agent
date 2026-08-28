package com.cherry.cherryaiagent.demo.invoke;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ChatModelInvoke {

    @Resource
    private OpenAiChatModel openAiChatModel;

    @Resource
    private DeepSeekChatModel deepSeekChatModel;

    public void doChatWithOpenAi() {
        String response = openAiChatModel
                .call(new Prompt("你好，我是cherry，你是什么模型，今天是什么日子"))
                .getResult()
                .getOutput()
                .getText();
        log.info("This is ai response : {}" , response);
    }

    public void doChatWithDeepSeek() {
        String response = deepSeekChatModel
                .call(new Prompt("你好，我是cherry，你是什么模型，今天是什么日子"))
                .getResult()
                .getOutput()
                .getText();
        log.info("This is ai response : {}" , response);
    }

}
