package com.jacolp.media.application.vo.image;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageBatchDeleteVO {
    private ArrayList<Long> successIds;
    private ArrayList<String> successFileNames;
    private ArrayList<Long> failIds;
    private ArrayList<String> failFileNames;
}
