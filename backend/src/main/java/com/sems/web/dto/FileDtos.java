package com.sems.web.dto;

import java.time.LocalDateTime;

/**
 * 图片附件 DTO（设计文档 5.4、5.5）。
 */
public final class FileDtos {

    private FileDtos() {
    }

    public record AttachmentView(
            Long attachmentId,
            String bizType,
            String bizTypeLabel,
            String fileName,
            String url,
            Long fileSize,
            String uploaderName,
            LocalDateTime uploadedAt,
            Integer sortNo) {
    }
}
