package com.sems.web;

import com.sems.common.ApiResponse;
import com.sems.common.PageResult;
import com.sems.service.EquipmentService;
import com.sems.web.dto.EquipmentDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 设备台账与设备二维码接口（EM002、EM010、EM014）。
 */
@RestController
@RequestMapping("/api/v1/equipments")
public class EquipmentController {

    private final EquipmentService equipmentService;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    @GetMapping
    public ApiResponse<PageResult<EquipmentDtos.EquipmentView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(equipmentService.list(keyword, department, enabled, page, size));
    }

    /**
     * 扫码识别设备：content 形如 EQ:SB-001。
     */
    @GetMapping("/by-qr")
    public ApiResponse<EquipmentDtos.QrScanView> byQr(@RequestParam String content) {
        return ApiResponse.ok(equipmentService.scan(content));
    }

    /**
     * 维修人员到场核对工单设备是否一致。
     */
    @GetMapping("/verify")
    public ApiResponse<EquipmentDtos.VerifyResult> verify(@RequestParam Long orderId,
                                                          @RequestParam String content) {
        return ApiResponse.ok(equipmentService.verify(orderId, content));
    }

    @GetMapping("/{equipmentId}")
    public ApiResponse<EquipmentDtos.EquipmentView> get(@PathVariable Long equipmentId) {
        return ApiResponse.ok(equipmentService.get(equipmentId));
    }

    @GetMapping("/{equipmentId}/history")
    public ApiResponse<EquipmentDtos.HistoryView> history(@PathVariable Long equipmentId) {
        return ApiResponse.ok(equipmentService.history(equipmentId));
    }

    @PostMapping
    public ApiResponse<EquipmentDtos.EquipmentView> create(@Valid @RequestBody EquipmentDtos.SaveRequest request) {
        return ApiResponse.ok(equipmentService.create(request));
    }

    @PutMapping("/{equipmentId}")
    public ApiResponse<EquipmentDtos.EquipmentView> update(@PathVariable Long equipmentId,
                                                           @Valid @RequestBody EquipmentDtos.SaveRequest request) {
        return ApiResponse.ok(equipmentService.update(equipmentId, request));
    }

    @PostMapping("/{equipmentId}/disable")
    public ApiResponse<EquipmentDtos.EquipmentView> disable(@PathVariable Long equipmentId) {
        return ApiResponse.ok(equipmentService.setEnabled(equipmentId, false));
    }

    @PostMapping("/{equipmentId}/enable")
    public ApiResponse<EquipmentDtos.EquipmentView> enable(@PathVariable Long equipmentId) {
        return ApiResponse.ok(equipmentService.setEnabled(equipmentId, true));
    }

    /**
     * 单台设备二维码图片（PNG），供预览、下载与打印。
     */
    @GetMapping("/{equipmentId}/qrcode")
    public ResponseEntity<byte[]> qrcode(@PathVariable Long equipmentId) {
        byte[] png = equipmentService.qrPng(equipmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"qrcode-" + equipmentId + ".png\"")
                .body(png);
    }

    /**
     * 批量生成二维码（单次不超过 50 台）。
     */
    @PostMapping("/qrcodes")
    public ApiResponse<List<EquipmentDtos.QrItem>> qrcodes(
            @Valid @RequestBody EquipmentDtos.QrBatchRequest request) {
        return ApiResponse.ok(equipmentService.buildQrItems(request.equipmentIds()));
    }
}
