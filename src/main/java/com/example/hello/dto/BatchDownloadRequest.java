package com.example.hello.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class BatchDownloadRequest {
    @NotEmpty(message = "ID列表不能为空")
    private List<Long> ids;

    public List<Long> getIds() {
        return ids;
    }

    public void setIds(List<Long> ids) {
        this.ids = ids;
    }
}
