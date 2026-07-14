package com.validdoc.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity //veritabaninda bu sınıf bır tabloya karsılık gelecek
@Table(name = "users") //tablonun adı users
@Data //lombok anotasyonu
@NoArgsConstructor //bos constructor uretır. jpa dbden verı cekerken kullanmak ıstıyor
@AllArgsConstructor //classın tum variablelarını parametre olarak kabul eden dolu bir constructor uretır. yenı class uyesı olustururken kolaylık saglar.
public class User {

    @Id //alttaki id degiskeni primary key (unique)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    //id degerleri veritabanı tarafından otomatık olarak sırayla atanacak
    private Long id;

    @Column(nullable = false, unique = true)
    //email alanı bos bırakılamaz, aynı emaıl adresınde ıkıncı bı kullanıcı kaydolamaz.
    private String email;

    @Column(nullable = false) //sifre bos bırakılamaz
    private String password;

    @Column(nullable = false) //rol boş bırakılamaz
    private String role; //admin | user
}


