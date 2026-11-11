package com.sems.repo;

import com.sems.entity.FileAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface FileAttachmentRepository extends JpaRepository<FileAttachment, Long> {

    List<FileAttachment> findByRequestIdOrderBySortNoAsc(Long requestId);

    List<FileAttachment> findByRecordIdOrderBySortNoAsc(Long recordId);

    List<FileAttachment> findByRecordIdInOrderByRecordIdAscSortNoAsc(Collection<Long> recordIds);

    List<FileAttachment> findByAttachmentIdIn(Collection<Long> attachmentIds);

    long countByRequestIdIsNullAndRecordIdIsNullAndUploaderId(Long uploaderId);

    List<FileAttachment> findByRequestIdIsNullAndRecordIdIsNullAndUploadedAtBefore(LocalDateTime deadline);

    long countByRequestId(Long requestId);

    /**
     * 关联报修单：只允许绑定尚未关联的附件（BR11）。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update FileAttachment a
               set a.requestId = :requestId
             where a.attachmentId = :attachmentId and a.requestId is null and a.recordId is null
            """)
    int bindToRequest(@Param("attachmentId") Long attachmentId, @Param("requestId") Long requestId);

    /**
     * 关联处理记录：完工照片绑定到本次处理记录。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update FileAttachment a
               set a.recordId = :recordId
             where a.attachmentId = :attachmentId and a.requestId is null and a.recordId is null
            """)
    int bindToRecord(@Param("attachmentId") Long attachmentId, @Param("recordId") Long recordId);
}
