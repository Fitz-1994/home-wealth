package com.homewealth.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // 通用
    SUCCESS(200, "success"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统内部错误"),

    // 用户
    USERNAME_EXISTS(1001, "用户名已存在"),
    USER_NOT_FOUND(1002, "用户不存在"),
    PASSWORD_INCORRECT(1003, "密码错误"),
    USER_DISABLED(1004, "账号已被禁用"),

    // 账户
    ACCOUNT_NOT_FOUND(2001, "账户不存在"),
    ACCOUNT_TYPE_INVALID(2002, "账户类型无效"),
    INVESTMENT_ACCOUNT_CATEGORY_MISMATCH(2003, "投资账户的资产类型必须为投资理财"),

    // 持仓
    HOLDING_NOT_FOUND(3001, "持仓记录不存在"),
    SYMBOL_INVALID(3002, "标的代码无效或无法获取价格"),
    NOT_INVESTMENT_ACCOUNT(3003, "该账户不是投资账户，不能添加持仓"),

    // 行情
    MARKET_DATA_FETCH_FAILED(4001, "行情数据获取失败"),

    // API Key
    API_KEY_NOT_FOUND(5001, "API Key不存在"),
    API_KEY_INVALID(5002, "API Key无效或已过期");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
