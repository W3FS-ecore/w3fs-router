package com.dig.common.dto;

import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author lp
 * @date 2022-06-22 16:48
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MinerInfo {

    private String enode;
    private String pubKey;
    private String peerId;
    private String peerAddr;

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
