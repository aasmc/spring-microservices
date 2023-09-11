package ru.aasmc.microservices.core.review.persistence;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.*;

@Entity
@Table(name = "reviews", indexes = {
        @Index(name = "reviews_unique_idx", unique = true, columnList = "productId,reviewId")
})
@NoArgsConstructor
@Getter
@Setter
public class ReviewEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @Version
    private int version;
    private int productId;
    private int reviewId;
    private String author;
    private String subject;
    private String content;

    public ReviewEntity(int productId, int reviewId, String author, String subject, String content) {
        this.productId = productId;
        this.reviewId = reviewId;
        this.author = author;
        this.subject = subject;
        this.content = content;
    }
}
