package com.sems.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.sems.entity.Equipment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * 设备二维码（EM014、BR07）：内容固定为 EQ:设备编号，由服务端统一生成。
 */
@Service
public class QrCodeService {

    private final String prefix;
    private final int size;

    public QrCodeService(@Value("${sems.qrcode.prefix:EQ:}") String prefix,
                         @Value("${sems.qrcode.size:300}") int size) {
        this.prefix = prefix;
        this.size = size;
    }

    public String content(Equipment equipment) {
        return content(equipment.getEquipmentCode());
    }

    public String content(String equipmentCode) {
        return prefix + equipmentCode;
    }

    public String prefix() {
        return prefix;
    }

    /**
     * 按设备编号生成固定尺寸、固定纠错级别的 PNG 图片，保证打印后可由手机识别。
     */
    public byte[] png(Equipment equipment) {
        try {
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.CHARACTER_SET, "UTF-8",
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter().encode(content(equipment), BarcodeFormat.QR_CODE, size, size, hints);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (WriterException | IOException ex) {
            throw new IllegalStateException("二维码生成失败", ex);
        }
    }
}
