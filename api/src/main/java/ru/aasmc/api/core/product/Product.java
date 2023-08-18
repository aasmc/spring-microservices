package ru.aasmc.api.core.product;

import lombok.*;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Setter
public class Product {
    private int productId;
    private String name;
    private int weight;
    private String serviceAddress;
}
