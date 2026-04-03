package com.homewealth.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class HoldingGroupVO {
    private Long id;
    private String groupName;
    private String note;
    /** 成员标的代码列表 */
    private List<GroupMemberVO> members;

    @Data
    public static class GroupMemberVO {
        private String symbol;
        private String symbolName;
        private String market;
    }
}
