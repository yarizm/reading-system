package com.example.reading.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * AI功能使用记录表
 * </p>
 *
 * @author CodingAssistant
 * @since 2026-01-31
 */
@Getter
@Setter
@TableName("ai_interaction_log")
public class AiInteractionLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("book_id")
    private Long bookId;

    /**
     * 功能类型: EXPLAIN(释意), REWRITE(改写), CONTINUE(续写), TTS(朗读)
     */
    @TableField("function_type")
    private String functionType;

    /**
     * 用户选中的文本片段(截取前200字)
     */
    @TableField("input_text_snippet")
    private String inputTextSnippet;

    @TableField("create_time")
    private LocalDateTime createTime;
}
