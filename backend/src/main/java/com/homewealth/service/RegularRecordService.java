package com.homewealth.service;

import com.homewealth.dto.request.UpdateRecordRequest;
import com.homewealth.model.RegularAccountRecord;

import java.util.List;

public interface RegularRecordService {
    RegularAccountRecord getCurrentRecord(Long userId, Long accountId);
    List<RegularAccountRecord> getHistory(Long userId, Long accountId);
    RegularAccountRecord addRecord(Long userId, Long accountId, UpdateRecordRequest request);
    void deleteRecord(Long userId, Long accountId, Long recordId);
}
