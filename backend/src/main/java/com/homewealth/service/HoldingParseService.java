package com.homewealth.service;

import com.homewealth.dto.response.ParsedHoldingVO;
import java.util.List;

public interface HoldingParseService {
    /**
     * 调用 Claude Vision API 解析持仓截图
     * @param imageBytes 图片字节数组
     * @param mimeType   如 image/png, image/jpeg
     * @return 解析出的持仓列表（未经用户确认）
     */
    List<ParsedHoldingVO> parseFromImage(byte[] imageBytes, String mimeType);
}
