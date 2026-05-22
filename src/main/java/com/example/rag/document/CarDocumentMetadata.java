package com.example.rag.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarDocumentMetadata {
    private String brand;
    private String model;
    private String series;
    private String year;
    private String priceRange;
    private String carType;
    private String fuelType;
    private List<String> tags;
    private List<String> competitors;
    private String targetUsers;
    private String salesPoints;
}
