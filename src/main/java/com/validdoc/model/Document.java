package com.validdoc.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id //primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY) //otomatık atanır
    private Long id;

    @Column(nullable = false) //dokuman adı bos bırakılamaz
    private String title;

    @Column(nullable = false) //yüklenen dosyanın sunucuda veya klasördeki pathi
    private String filePath;

    @Column(nullable = false) //file type bos kalamaz
    private String fileType; //pdf | docx vs.

    @Column(nullable = false)
    private LocalDateTime uploadDate; //belgenın sısteme yuklenme saatı ve tarıhı

    @Column(nullable = false)
    private String status; //PENDING | APPROVED | REJECTED

    @ManyToOne //tek bır usera ait birden fazla dokuman olabılır.
    @JoinColumn(name = "user_id", nullable = false) //sahıpsız dokuman yuklenemez
    private User user;
}