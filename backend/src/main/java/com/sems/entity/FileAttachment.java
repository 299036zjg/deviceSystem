package com.sems.entity;

import com.sems.domain.BizType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 图片附件（设计文档 4.7）：先上传后关联，只追加不修改。
 * REPAIR 关联报修单（request_id），WORK 关联处理记录（record_id），二者互斥。
 */
@Entity
@Table(name = "file_attachment")
@Getter
@Setter
@NoArgsConstructor
public class FileAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attachment_id")
    private Long attachmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "biz_type", nullable = false, length = 12)
    private BizType bizType;

    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "record_id")
    private Long recordId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 255)
    private String filePath;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 1;
}
