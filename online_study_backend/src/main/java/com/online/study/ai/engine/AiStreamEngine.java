package com.online.study.ai.engine;

import com.online.study.vo.AiAnswerVO;

/**
 * 流式问答能力 —— 引擎的「可选」能力，所以单独做成一个接口。
 *
 * <h3>为什么不把 {@code stream} 直接加进 {@link AiEngine}</h3>
 * {@link AiEngine} 的四个方法（{@code type/ready/unavailableReason/ask}）是<b>每个引擎都必须有</b>的；
 * 流式是<b>能力差异</b>：legacy 引擎的 {@code AiChatClient} 里 {@code stream=false} 是写死的，
 * 它给不出增量输出。如果把 {@code stream} 塞进 {@link AiEngine}，legacy 就只能抛
 * {@code UnsupportedOperationException} 或者写一个假实现 —— 两种都在骗调用方。
 * 拆成独立接口后，"谁能流、谁不能流"由 {@code instanceof} 一次判清，
 * 上层（{@code AiAssistantService}）据此决定是流式推还是整段推。
 *
 * <h3>为什么回调而不是返回 {@code SseEmitter} / {@code Flux}</h3>
 * <ol>
 *   <li><b>依赖方向</b>：{@code ai} 包不该认识 Spring MVC（{@code SseEmitter} 是 web 层的东西）。
 *       引擎只管"我这儿来了一段字"，怎么送到浏览器是接口层的事。</li>
 *   <li><b>两种传输共用一份逻辑</b>：将来如果换 WebFlux，把回调包成 {@code Flux} 即可，
 *       引擎一行不用改。</li>
 *   <li><b>可测</b>：测试里给一个把 delta 收集到 List 的 sink，就能断言"分了 3 段以上"，
 *       不需要起容器、不需要真发 HTTP。</li>
 * </ol>
 *
 * <h3>三条约定（实现方必须遵守）</h3>
 * <ol>
 *   <li>{@link StreamSink#onDelta} 可能被调用 0~N 次，文字是<b>累加</b>的增量片段，
 *       调用方直接往后拼就是完整答案，不需要自己算偏移。</li>
 *   <li>{@link StreamSink#onComplete} 与 {@link StreamSink#onError} <b>二选一、只调一次</b>，
 *       且必须是最后被调用的那个。</li>
 *   <li>完成时传的 {@link AiAnswerVO} 是<b>完整结果</b>（答案全文 + 出处 + 工具轨迹 + token），
 *       前端拿它去渲染引用列表 —— 增量的 delta 里只有正文，没有出处。</li>
 * </ol>
 */
public interface AiStreamEngine {

    /**
     * 流式回答一个问题。
     *
     * <p>身份同样不从参数传：实现内部从 JWT 取（同 {@link AiEngine#ask}）。
     * <b>调用方必须保证执行线程上带着 SecurityContext</b>（异步线程要显式传播），
     * 否则工具会查不到"我是谁"。
     *
     * @param question 用户原话
     * @param sink     增量输出的出口
     */
    void askStream(String question, StreamSink sink);

    /**
     * 流式输出的出口。三个方法都在<b>同一个线程</b>上被顺序调用，实现方不需要考虑并发。
     */
    interface StreamSink {

        /**
         * 上下文已就绪（可选，0 或 1 次）：模型名、知识块数、命中的出处<b>都定了，但还没开始生成</b>。
         *
         * <p>为什么要单独给一次：检索发生在生成之前，出处（sources）在第一个字吐出来之前就有了。
         * 有了这一拍，前端可以在"正在输入…"的同时把引用列表渲染出来，
         * 而不是等全文结束才一起蹦出来 —— 这是流式体验的一半。
         *
         * <p>传进来的 {@link AiAnswerVO} 里 {@code answer} 为 null（正文还没生成），
         * 其余字段（{@code model/chunkCount/hitCount/sources}）都已填好。
         *
         * <p>不支持细粒度阶段的实现（比如 legacy 一次性推完）可以<b>不调用</b>它。
         */
        void onMeta(AiAnswerVO context);

        /** 一段增量正文。注意可能是半个词、半个句子，调用方逐段追加显示即可 */
        void onDelta(String text);

        /** 正常结束，带完整结果（含出处、工具轨迹、token） */
        void onComplete(AiAnswerVO full);

        /** 出错。调用方据此决定是提示重试还是降级 */
        void onError(Throwable error);
    }
}
