package com.validdoc.model;

import jakarta.persistence.*;

@Entity
@Table(name = "templates")
public class Template {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String fieldDefinitions;

    public Template() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFieldDefinitions() { return fieldDefinitions; }
    public void setFieldDefinitions(String fieldDefinitions) { this.fieldDefinitions = fieldDefinitions; }
}