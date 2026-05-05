package com.example.hello.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AppReviewRequest {

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分必须在 1-5 之间")
    @Max(value = 5, message = "评分必须在 1-5 之间")
    private Integer rating;

    @Size(max = 500, message = "评论最长 500 字符")
    private String comment;

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
