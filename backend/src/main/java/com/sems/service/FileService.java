package com.sems.service;

import com.sems.common.BizException;
import com.sems.common.ErrorCode;
import com.sems.domain.BizType;
import com.sems.domain.Role;
import com.sems.entity.FileAttachment;
import com.sems.entity.RepairRequest;
import com.sems.entity.User;
import com.sems.entity.WorkOrder;
import com.sems.entity.WorkRecord;
import com.sems.repo.FileAttachmentRepository;
import com.sems.repo.RepairRequestRepository;
import com.sems.repo.UserRepository;
import com.sems.repo.WorkOrderRepository;
import com.sems.repo.WorkRecordRepository;
import com.sems.security.AuthContext;
import com.sems.security.Session;
import com.sems.web.dto.FileDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 图片附件服务（设计文档 4.7、5.5、BR08、BR11）。
 * 上传时只保存文件与元数据，业务提交时在同一事务内建立关联；访问须经令牌与对象范围校验。
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png");
    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM");

    private final Path root;
    private final int maxPerRequest;
    private final long maxSizeBytes;
    private final int retentionHours;

    private final FileAttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final WorkRecordRepository workRecordRepository;
    private final WorkOrderRepository workOrderRepository;

    public FileService(@Value("${sems.storage.root:./data/uploads}") String rootDir,
                       @Value("${sems.attachment.max-per-request:6}") int maxPerRequest,
                       @Value("${sems.attachment.max-size-bytes:5242880}") long maxSizeBytes,
                       @Value("${sems.attachment.retention-hours:24}") int retentionHours,
                       FileAttachmentRepository attachmentRepository,
                       UserRepository userRepository,
                       RepairRequestRepository repairRequestRepository,
                       WorkRecordRepository workRecordRepository,
                       WorkOrderRepository workOrderRepository) {
        this.root = Paths.get(rootDir).toAbsolutePath().normalize();
        this.maxPerRequest = maxPerRequest;
        this.maxSizeBytes = maxSizeBytes;
        this.retentionHours = retentionHours;
        this.attachmentRepository = attachmentRepository;
        this.userRepository = userRepository;
        this.repairRequestRepository = repairRequestRepository;
        this.workRecordRepository = workRecordRepository;
        this.workOrderRepository = workOrderRepository;
    }

    /**
     * 上传单张图片：校验格式与大小，重新编码后随机命名保存（5.5）。
     */
    @Transactional
    public FileDtos.AttachmentView upload(MultipartFile file, String bizTypeValue, Integer sortNo) {
        Session session = AuthContext.current();
        if (session.role() == Role.ADMIN) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN, "管理员不参与上传图片，请在报修或维修提交时由相应角色上传");
        }
        BizType bizType = parseBizType(bizTypeValue);
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择要上传的图片");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new BizException(ErrorCode.FILE_LIMIT, "单张图片不能超过 5 MB，请压缩后重试");
        }
        if (attachmentRepository.countByRequestIdIsNullAndRecordIdIsNullAndUploaderId(session.userId())
                >= maxPerRequest) {
            throw new BizException(ErrorCode.FILE_LIMIT,
                    "单次最多上传 " + maxPerRequest + " 张照片，请先提交当前单据");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new BizException(ErrorCode.FILE_LIMIT, "仅支持 JPG、PNG 格式的图片");
        }
        BufferedImage image;
        try (InputStream in = file.getInputStream()) {
            image = ImageIO.read(in);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "图片读取失败，请重新选择文件");
        }
        if (image == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件内容不是有效图片");
        }
        boolean png = "image/png".equals(contentType);
        byte[] encoded = encode(image, png);

        String dateDir = LocalDateTime.now().format(DATE_DIR);
        String storedName = UUID.randomUUID().toString().replace("-", "") + (png ? ".png" : ".jpg");
        String relativePath = dateDir + "/" + storedName;
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法的存储路径");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, encoded);
        } catch (IOException ex) {
            log.error("保存图片失败 {}", target, ex);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "图片保存失败，请稍后重试");
        }

        FileAttachment attachment = new FileAttachment();
        attachment.setBizType(bizType);
        attachment.setFileName(safeFileName(file.getOriginalFilename()));
        attachment.setFilePath(relativePath);
        attachment.setContentType(png ? "image/png" : "image/jpeg");
        attachment.setFileSize((long) encoded.length);
        attachment.setUploaderId(session.userId());
        attachment.setUploadedAt(LocalDateTime.now());
        attachment.setSortNo(sortNo == null || sortNo < 1 ? 1 : sortNo);
        return toView(attachmentRepository.save(attachment), new HashMap<>());
    }

    /**
     * 读取图片：校验角色与对象范围后返回文件流（BR12、5.5）。
     */
    @Transactional(readOnly = true)
    public FileContent read(Long attachmentId, Integer width) {
        FileAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> BizException.notFound("图片不存在或已被回收"));
        assertReadable(attachment);
        Path path = root.resolve(attachment.getFilePath()).normalize();
        if (!path.startsWith(root) || !Files.exists(path)) {
            throw BizException.notFound("图片文件不存在，请联系管理员");
        }
        byte[] data;
        try {
            data = Files.readAllBytes(path);
        } catch (IOException ex) {
            log.error("读取图片失败 {}", path, ex);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "图片读取失败");
        }
        String contentType = attachment.getContentType();
        if (width != null && width > 0 && width <= 1000) {
            byte[] scaled = thumbnail(data, width);
            if (scaled != null) {
                data = scaled;
                contentType = "image/jpeg";
            }
        }
        return new FileContent(data, contentType);
    }

    /**
     * 报修单照片关联（BR11）：只能关联本人上传且未被使用的附件。
     */
    @Transactional
    public void bindToRequest(List<Long> attachmentIds, Long requestId) {
        bind(attachmentIds, BizType.REPAIR, requestId, null);
    }

    /**
     * 完工照片关联到处理记录。
     */
    @Transactional
    public void bindToRecord(List<Long> attachmentIds, Long recordId) {
        bind(attachmentIds, BizType.WORK, null, recordId);
    }

    private void bind(List<Long> attachmentIds, BizType expectedType, Long requestId, Long recordId) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        if (attachmentIds.size() > maxPerRequest) {
            throw new BizException(ErrorCode.FILE_LIMIT, "单次最多提交 " + maxPerRequest + " 张照片");
        }
        Long userId = AuthContext.userId();
        for (Long attachmentId : attachmentIds) {
            FileAttachment attachment = attachmentRepository.findById(attachmentId)
                    .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "图片附件不存在：" + attachmentId));
            if (!attachment.getUploaderId().equals(userId)) {
                throw BizException.outOfScope("不能引用他人上传的图片");
            }
            if (attachment.getBizType() != expectedType) {
                throw new BizException(ErrorCode.PARAM_INVALID, "图片附件类型不匹配：" + attachmentId);
            }
            if (attachment.getRequestId() != null || attachment.getRecordId() != null) {
                throw BizException.conflict("图片附件已关联其他单据：" + attachmentId);
            }
            int updated = requestId != null
                    ? attachmentRepository.bindToRequest(attachmentId, requestId)
                    : attachmentRepository.bindToRecord(attachmentId, recordId);
            if (updated == 0) {
                throw BizException.conflict("图片附件已被使用：" + attachmentId);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<FileDtos.AttachmentView> views(List<FileAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = new HashMap<>();
        userRepository.findAllById(attachments.stream().map(FileAttachment::getUploaderId).distinct().toList())
                .forEach(u -> names.put(u.getUserId(), u.getName()));
        return attachments.stream().map(a -> toView(a, names)).toList();
    }

    @Transactional(readOnly = true)
    public List<FileDtos.AttachmentView> viewsOfRequest(Long requestId) {
        return views(attachmentRepository.findByRequestIdOrderBySortNoAsc(requestId));
    }

    @Transactional(readOnly = true)
    public List<FileDtos.AttachmentView> viewsOfWorkRecords(List<WorkRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<Long> recordIds = records.stream().map(WorkRecord::getRecordId).toList();
        return views(attachmentRepository.findByRecordIdInOrderByRecordIdAscSortNoAsc(recordIds));
    }

    /**
     * 定时回收未被单据引用且超时的附件（BR08、BR11）。
     */
    @Scheduled(initialDelay = 5, fixedDelayString = "${sems.attachment.cleanup-interval-minutes:60}",
            timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void cleanupUnbound() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(retentionHours);
        List<FileAttachment> candidates =
                attachmentRepository.findByRequestIdIsNullAndRecordIdIsNullAndUploadedAtBefore(deadline);
        int removed = 0;
        for (FileAttachment attachment : candidates) {
            Path path = root.resolve(attachment.getFilePath()).normalize();
            try {
                if (path.startsWith(root)) {
                    Files.deleteIfExists(path);
                }
                attachmentRepository.delete(attachment);
                removed++;
            } catch (IOException ex) {
                log.warn("回收附件失败 {}", path, ex);
            }
        }
        if (removed > 0) {
            log.info("清理未关联图片附件 {} 个", removed);
        }
    }

    /**
     * 访问控制：管理员全部可见；上传人、报修人、工单负责人按对象范围可见（BR12）。
     */
    private void assertReadable(FileAttachment attachment) {
        Session session = AuthContext.current();
        if (session.role() == Role.ADMIN) {
            return;
        }
        if (attachment.getUploaderId().equals(session.userId())) {
            return;
        }
        if (attachment.getRequestId() != null) {
            RepairRequest request = repairRequestRepository.findById(attachment.getRequestId()).orElse(null);
            if (request != null && request.getReporterId().equals(session.userId())) {
                return;
            }
        }
        if (attachment.getRecordId() != null) {
            WorkRecord record = workRecordRepository.findById(attachment.getRecordId()).orElse(null);
            if (record != null && record.getOperatorId().equals(session.userId())) {
                return;
            }
            if (record != null) {
                WorkOrder order = workOrderRepository.findById(record.getOrderId()).orElse(null);
                if (order != null && order.getOwnerId().equals(session.userId())) {
                    return;
                }
            }
        }
        throw BizException.outOfScope("无权查看该图片");
    }

    private FileDtos.AttachmentView toView(FileAttachment attachment, Map<Long, String> names) {
        String uploaderName = names.computeIfAbsent(attachment.getUploaderId(),
                id -> userRepository.findById(id).map(User::getName).orElse("未知用户"));
        return new FileDtos.AttachmentView(attachment.getAttachmentId(), attachment.getBizType().name(),
                attachment.getBizType().label(), attachment.getFileName(),
                "/api/v1/files/" + attachment.getAttachmentId(), attachment.getFileSize(),
                uploaderName, attachment.getUploadedAt(), attachment.getSortNo());
    }

    private byte[] encode(BufferedImage image, boolean png) {
        BufferedImage target = image;
        if (!png && image.getType() != BufferedImage.TYPE_INT_RGB) {
            target = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = target.createGraphics();
            g.drawImage(image, 0, 0, java.awt.Color.WHITE, null);
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(target, png ? "png" : "jpg", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "图片处理失败");
        }
    }

    private byte[] thumbnail(byte[] data, int width) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(data));
            if (source == null || source.getWidth() <= width) {
                return null;
            }
            int height = Math.max(1, source.getHeight() * width / source.getWidth());
            BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, width, height, java.awt.Color.WHITE, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(scaled, "jpg", out);
            return out.toByteArray();
        } catch (IOException ex) {
            return null;
        }
    }

    private BizType parseBizType(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "图片类型（bizType）不能为空，取值为 REPAIR 或 WORK");
        }
        try {
            return BizType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "图片类型只能为 REPAIR（故障现场）或 WORK（维修完成）");
        }
    }

    private String safeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "image";
        }
        String name = original.replaceAll("[\\\\/:*?\"<>|]", "_");
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    /**
     * 文件内容与类型。
     */
    public record FileContent(byte[] data, String contentType) {
    }
}
