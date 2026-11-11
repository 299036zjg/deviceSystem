package com.sems.web;

import com.sems.common.ApiResponse;
import com.sems.service.FileService;
import com.sems.web.dto.FileDtos;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片上传与鉴权访问接口（设计文档 5.5、BR08、BR12）。
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileDtos.AttachmentView> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "sortNo", required = false) Integer sortNo) {
        return ApiResponse.ok(fileService.upload(file, bizType, sortNo));
    }

    /**
     * 读取图片；width 用于列表缩略图，详情不传该参数读取原图。
     */
    @GetMapping("/{attachmentId}")
    public ResponseEntity<byte[]> read(@PathVariable Long attachmentId,
                                       @RequestParam(required = false) Integer width) {
        FileService.FileContent content = fileService.read(attachmentId, width);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=600")
                .body(content.data());
    }
}
