package com.example.animemanager.DTO;

import lombok.Data;

@Data
public class ImportDTO {
    private Long subjectId;
    private String subjectJson;
    private String characterJson;
    private String personJson;
    private String episodeJson;
}
