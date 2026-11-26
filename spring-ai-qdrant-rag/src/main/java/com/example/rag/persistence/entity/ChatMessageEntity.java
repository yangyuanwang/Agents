package com.example.rag.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import java.util.UUID;

@TableName(value = "chat_message", autoResultMap = true)
public class ChatMessageEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;
    @TableField("session_id")
    private UUID sessionId;
    @TableField("sequence_no")
    private Long sequenceNo;
    @TableField("role")
    private String role;
    @TableField("content")
    private String content;
    @TableField("status")
    private String status;
    @TableField("model_name")
    private String modelName;
    @TableField("token_input")
    private Integer tokenInput;
    @TableField("token_output")
    private Integer tokenOutput;
    @TableField("error_code")
    private String errorCode;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("completed_at")
    private LocalDateTime completedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public Long getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(Long sequenceNo) { this.sequenceNo = sequenceNo; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public Integer getTokenInput() { return tokenInput; }
    public void setTokenInput(Integer tokenInput) { this.tokenInput = tokenInput; }

    public Integer getTokenOutput() { return tokenOutput; }
    public void setTokenOutput(Integer tokenOutput) { this.tokenOutput = tokenOutput; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
